package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.Folder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Durchsucht den Bibliotheksordner und baut daraus einen unveränderlichen Index. */
@Component
class LibraryScanner {

    private static final Logger log = LoggerFactory.getLogger(LibraryScanner.class);

    /**
     * Formate, die die Standard-ImageIO des JDK lesen kann. WebP und AVIF fehlen hier bewusst: Ohne
     * native Zusatzbibliothek könnten wir davon weder Maße noch Thumbnails erzeugen, und eine Kachel,
     * die mitten in der Sitzung schwarz bleibt, ist schlimmer als eine Datei, die gar nicht auftaucht.
     * Solche Dateien werden stattdessen beim Scan protokolliert.
     */
    private static final Set<String> SUPPORTED = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private static final Set<String> UNSUPPORTED_IMAGES = Set.of("webp", "avif", "heic", "heif", "tif", "tiff");

    private static final String ROOT_FOLDER_NAME = "Allgemein";

    LibraryIndex scan(Path root) {
        if (!Files.isDirectory(root)) {
            log.warn("Bibliotheksordner {} existiert nicht — Bibliothek bleibt leer.", root.toAbsolutePath());
            return LibraryIndex.empty();
        }

        List<Asset> assets = new ArrayList<>();
        Map<String, Integer> skipped = new TreeMap<>();

        try (Stream<Path> walk = Files.walk(root)) {
            Iterator<Path> it = walk.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                Path file = it.next();
                if (isHidden(root, file)) {
                    continue;
                }
                String extension = extensionOf(file);
                if (!SUPPORTED.contains(extension)) {
                    if (UNSUPPORTED_IMAGES.contains(extension)) {
                        skipped.merge(extension, 1, Integer::sum);
                    }
                    continue;
                }
                toAsset(root, file).ifPresent(assets::add);
            }
        } catch (IOException e) {
            log.error("Bibliotheksordner {} konnte nicht gelesen werden.", root.toAbsolutePath(), e);
            return LibraryIndex.empty();
        }

        skipped.forEach((extension, count) ->
                log.warn("{} Datei(en) mit Endung .{} übersprungen — dieses Format wird nicht unterstützt. "
                        + "Bitte als JPEG oder PNG ablegen.", count, extension));

        return index(assets);
    }

    private static LibraryIndex index(List<Asset> assets) {
        assets.sort(Comparator.comparing(Asset::folder, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Asset::displayName, String.CASE_INSENSITIVE_ORDER));

        Map<String, Asset> byId = new HashMap<>();
        Map<String, List<Asset>> byFolder = new LinkedHashMap<>();
        for (Asset asset : assets) {
            Asset clash = byId.putIfAbsent(asset.id(), asset);
            if (clash != null) {
                log.error("ID-Kollision zwischen {} und {} — die zweite Datei wird ignoriert.",
                        clash.absolutePath(), asset.absolutePath());
                continue;
            }
            byFolder.computeIfAbsent(asset.folder(), key -> new ArrayList<>()).add(asset);
        }

        List<Folder> folders = byFolder.entrySet().stream()
                .map(entry -> new Folder(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();

        return new LibraryIndex(Map.copyOf(byId), folders);
    }

    private static java.util.Optional<Asset> toAsset(Path root, Path file) {
        try {
            String relativePath = root.relativize(file).toString().replace('\\', '/');
            long size = Files.size(file);
            var lastModified = Files.getLastModifiedTime(file).toInstant();
            int[] dimensions = readDimensions(file);

            return java.util.Optional.of(new Asset(
                    hash(relativePath, 16),
                    hash(lastModified.toEpochMilli() + ":" + size, 12),
                    folderOf(relativePath),
                    displayNameOf(file),
                    file,
                    dimensions[0],
                    dimensions[1],
                    size,
                    lastModified));
        } catch (IOException e) {
            log.warn("Datei {} konnte nicht gelesen werden und wird übersprungen.", file, e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Liest nur den Kopf der Datei, statt das ganze Bild zu dekodieren — bei einer Bibliothek voller
     * mehrere Megapixel großer Bilder ist das der Unterschied zwischen einem Scan von Sekunden und
     * einem von Minuten auf dem Pi.
     */
    private static int[] readDimensions(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return new int[] {0, 0};
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return new int[] {0, 0};
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Maße von {} nicht lesbar.", file, e);
            return new int[] {0, 0};
        }
    }

    private static boolean isHidden(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String folderOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? ROOT_FOLDER_NAME : relativePath.substring(0, slash);
    }

    private static String displayNameOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String hash(String input, int length) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, length);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser JVM", e);
        }
    }
}

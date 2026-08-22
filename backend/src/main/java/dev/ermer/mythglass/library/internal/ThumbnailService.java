package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Erzeugt und cached Vorschaubilder.
 *
 * <p>Ohne Thumbnails schiebt die Steuerungsoberfläche mehrere Megabyte pro Kachel über das WLAN — bei
 * einer Bibliothek mit ein paar hundert Bildern macht das den Unterschied zwischen einer bedienbaren
 * und einer unbrauchbaren Liste auf dem Handy.
 */
@Component
class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    private static final int MAX_EDGE = 480;
    private static final double QUALITY = 0.8;

    private final Path thumbnailDir;
    private final ExecutorService warmUpExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "thumbnail-warmup");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Future<?>> warmUpTask = new AtomicReference<>();

    ThumbnailService(LibraryProperties properties) {
        this.thumbnailDir = properties.cache().toAbsolutePath().normalize().resolve("thumbs");
    }

    /**
     * Liefert den Pfad zum Vorschaubild und erzeugt es, falls es fehlt oder veraltet ist.
     *
     * @throws IOException wenn das Bild nicht gelesen oder das Thumbnail nicht geschrieben werden kann
     */
    Path thumbnailFor(Asset asset) throws IOException {
        Path target = thumbnailDir.resolve(fileNameFor(asset));
        if (Files.isRegularFile(target)) {
            return target;
        }
        generate(asset, target);
        return target;
    }

    /**
     * Erzeugt im Hintergrund alle noch fehlenden Vorschaubilder, damit der erste Griff in einen Ordner
     * am Spieltisch nicht auf die Skalierung wartet. Bewusst einspurig: Auf dem Pi soll das Vorwärmen
     * nicht mit den Anfragen konkurrieren, die gerade wirklich jemand sieht.
     */
    void warmUp(Collection<Asset> assets) {
        Future<?> previous = warmUpTask.getAndSet(null);
        if (previous != null) {
            previous.cancel(true);
        }
        if (assets.isEmpty()) {
            return;
        }
        warmUpTask.set(warmUpExecutor.submit(() -> {
            Set<String> expected = new HashSet<>();
            int generated = 0;
            for (Asset asset : assets) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                Path target = thumbnailDir.resolve(fileNameFor(asset));
                expected.add(target.getFileName().toString());
                if (Files.isRegularFile(target)) {
                    continue;
                }
                try {
                    generate(asset, target);
                    generated++;
                } catch (IOException e) {
                    log.warn("Vorschaubild für {} konnte nicht erzeugt werden.", asset.absolutePath(), e);
                }
            }
            if (generated > 0) {
                log.info("{} Vorschaubild(er) im Hintergrund erzeugt.", generated);
            }
            prune(expected);
        }));
    }

    /**
     * Entfernt Vorschaubilder, zu denen es kein Bild mehr gibt. Da der Dateiname die Version enthält,
     * hinterlässt jedes ersetzte Bild sonst dauerhaft eine Leiche auf der SD-Karte des Pi.
     */
    private void prune(Set<String> expected) {
        if (!Files.isDirectory(thumbnailDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(thumbnailDir)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".jpg"))
                    .filter(file -> !expected.contains(file.getFileName().toString()))
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            log.debug("Verwaistes Vorschaubild {} nicht löschbar.", file, e);
                        }
                    });
        } catch (IOException e) {
            log.debug("Thumbnail-Cache {} nicht aufräumbar.", thumbnailDir, e);
        }
    }

    private static String fileNameFor(Asset asset) {
        return asset.id() + "-" + asset.version() + ".jpg";
    }

    private void generate(Asset asset, Path target) throws IOException {
        Files.createDirectories(thumbnailDir);
        // Erst in eine temporäre Datei, dann atomar umbenennen: Zwei gleichzeitige Anfragen auf dasselbe
        // fehlende Thumbnail dürfen sich nicht gegenseitig eine halb geschriebene Datei unterschieben.
        Path temporary = Files.createTempFile(thumbnailDir, asset.id(), ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                Thumbnails.of(asset.absolutePath().toFile())
                        .size(MAX_EDGE, MAX_EDGE)
                        // PNGs mit Transparenz lassen sich nicht direkt als JPEG schreiben; ohne dieses
                        // erzwungene RGB scheitert ImageIO an solchen Dateien.
                        .imageType(BufferedImage.TYPE_INT_RGB)
                        .outputFormat("jpg")
                        .outputQuality(QUALITY)
                        .toOutputStream(out);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @PreDestroy
    void shutdown() {
        warmUpExecutor.shutdownNow();
    }
}

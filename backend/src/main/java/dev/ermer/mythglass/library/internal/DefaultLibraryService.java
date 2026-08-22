package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.Folder;
import dev.ermer.mythglass.library.LibraryRescanned;
import dev.ermer.mythglass.library.LibraryService;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class DefaultLibraryService implements LibraryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLibraryService.class);

    private final LibraryScanner scanner;
    private final ThumbnailService thumbnails;
    private final ApplicationEventPublisher events;
    private final Path root;

    private final AtomicReference<LibraryIndex> index = new AtomicReference<>(LibraryIndex.empty());

    DefaultLibraryService(LibraryScanner scanner, ThumbnailService thumbnails,
            ApplicationEventPublisher events, LibraryProperties properties) {
        this.scanner = scanner;
        this.thumbnails = thumbnails;
        this.events = events;
        this.root = properties.path().toAbsolutePath().normalize();
    }

    /** Beim Start einlesen, damit die Weboberfläche nie eine leere Bibliothek zeigt, die nur nicht fertig ist. */
    @PostConstruct
    void scanOnStartup() {
        rescan();
    }

    @Override
    public List<Folder> folders() {
        return index.get().folders();
    }

    @Override
    public Optional<Asset> find(String assetId) {
        return Optional.ofNullable(index.get().byId().get(assetId));
    }

    @Override
    public boolean contains(String assetId) {
        return index.get().byId().containsKey(assetId);
    }

    @Override
    public int rescan() {
        LibraryIndex scanned = scanner.scan(root);
        index.set(scanned);
        log.info("Bibliothek eingelesen: {} Bild(er) in {} Ordner(n) unter {}",
                scanned.size(), scanned.folders().size(), root);

        thumbnails.warmUp(scanned.byId().values());
        events.publishEvent(new LibraryRescanned(scanned.size()));
        return scanned.size();
    }

    /** Wurzel der Bibliothek, normalisiert — Grundlage der Pfadprüfung beim Ausliefern. */
    Path root() {
        return root;
    }
}

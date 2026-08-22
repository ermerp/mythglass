package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.LibraryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
class LibraryController {

    private final LibraryService library;

    LibraryController(LibraryService library) {
        this.library = library;
    }

    @GetMapping
    LibraryView library() {
        return toView(library.folders());
    }

    @PostMapping("/rescan")
    RescanResult rescan() {
        return new RescanResult(library.rescan());
    }

    private static LibraryView toView(List<dev.ermer.mythglass.library.Folder> folders) {
        return new LibraryView(folders.stream()
                .map(folder -> new FolderView(folder.name(), folder.assets().stream().map(LibraryController::toView).toList()))
                .toList());
    }

    /** Bewusst ohne Dateipfad: Der Client soll Bilder ausschließlich über die ID adressieren. */
    private static AssetView toView(Asset asset) {
        return new AssetView(asset.id(), asset.displayName(), asset.width(), asset.height());
    }

    record LibraryView(List<FolderView> folders) {}

    record FolderView(String name, List<AssetView> assets) {}

    record AssetView(String id, String name, int width, int height) {}

    record RescanResult(int assetCount) {}
}

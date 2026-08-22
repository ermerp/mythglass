package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.LibraryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Liefert Bilder und Vorschaubilder aus.
 *
 * <p>Auslieferung erfolgt ausschließlich über die ID: Der Pfad wird aus dem Index aufgelöst, nie aus
 * dem Request übernommen. Zusätzlich wird geprüft, dass der aufgelöste Pfad unterhalb der Bibliothek
 * liegt — ein Symlink im Bilderordner soll nicht das halbe Dateisystem über HTTP verfügbar machen.
 */
@RestController
@RequestMapping("/api/assets/{id}")
class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);

    private final LibraryService library;
    private final ThumbnailService thumbnails;
    private final Path libraryRoot;
    private final Path thumbnailRoot;

    AssetController(LibraryService library, ThumbnailService thumbnails, LibraryProperties properties) {
        this.library = library;
        this.thumbnails = thumbnails;
        this.libraryRoot = properties.path().toAbsolutePath().normalize();
        this.thumbnailRoot = properties.cache().toAbsolutePath().normalize();
    }

    @GetMapping("/full")
    ResponseEntity<Resource> full(@PathVariable String id, WebRequest request) {
        Asset asset = require(id);
        return serve(asset.absolutePath(), libraryRoot, asset.version(), request);
    }

    @GetMapping("/thumb")
    ResponseEntity<Resource> thumb(@PathVariable String id, WebRequest request) {
        Asset asset = require(id);
        try {
            return serve(thumbnails.thumbnailFor(asset), thumbnailRoot, asset.version(), request);
        } catch (IOException e) {
            log.warn("Vorschaubild für {} nicht erzeugbar — liefere das Original.", asset.absolutePath(), e);
            return serve(asset.absolutePath(), libraryRoot, asset.version(), request);
        }
    }

    private Asset require(String id) {
        return library.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unbekanntes Bild: " + id));
    }

    private ResponseEntity<Resource> serve(Path file, Path allowedRoot, String version, WebRequest request) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(allowedRoot)) {
            log.error("Auslieferung von {} abgelehnt: liegt außerhalb von {}", normalized, allowedRoot);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unbekanntes Bild");
        }
        if (!Files.isReadable(normalized)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Datei nicht mehr lesbar: " + normalized.getFileName());
        }

        String etag = "\"" + version + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(normalized.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .eTag(etag)
                // Der Browser darf zwischenspeichern, muss aber kurz rückfragen. Im LAN kostet das
                // wenige Millisekunden und erspart die Frage, warum ein ausgetauschtes Bild noch das
                // alte Motiv zeigt.
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(normalized));
    }
}

package dev.ermer.mythglass.library;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Ein Bild aus der Bibliothek.
 *
 * <p>Die {@code id} wird aus dem relativen Pfad abgeleitet und ist damit über einen Rescan hinweg
 * stabil — der Spielleiter kann dieselbe Kachel nach dem Neueinlesen wiederfinden. Die
 * {@code version} leitet sich dagegen aus Änderungszeit und Größe ab und ändert sich, sobald eine
 * Datei durch eine andere mit gleichem Namen ersetzt wird. Sie dient als ETag.
 */
public record Asset(
        String id,
        String version,
        String folder,
        String displayName,
        Path absolutePath,
        int width,
        int height,
        long sizeBytes,
        Instant lastModified) {}

package dev.ermer.mythglass.library;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf die Bilderbibliothek.
 *
 * <p>Die Bibliothek ist ein Ordner im Dateisystem, kein Datenbestand der Anwendung: Der Spielleiter
 * legt Bilder per Netzwerkfreigabe ab, die Anwendung liest sie nur. Der Index lebt im Speicher und
 * wird beim Start sowie auf Anforderung neu aufgebaut.
 */
public interface LibraryService {

    /** Alle Ordner mit ihren Bildern, alphabetisch sortiert. */
    List<Folder> folders();

    Optional<Asset> find(String assetId);

    /**
     * Findet ein Bild über seinen Anzeigenamen, also den Dateinamen ohne Endung. Groß- und
     * Kleinschreibung spielen keine Rolle; Bilder im Wurzelordner haben Vorrang.
     *
     * <p>Damit lässt sich ein Bild in der Konfiguration benennen, ohne seine technische ID zu kennen
     * — der Spielleiter legt einfach eine Datei mit dem passenden Namen ab.
     */
    Optional<Asset> findByDisplayName(String displayName);

    boolean contains(String assetId);

    /** Baut den Index neu auf und liefert die Anzahl gefundener Bilder. */
    int rescan();
}

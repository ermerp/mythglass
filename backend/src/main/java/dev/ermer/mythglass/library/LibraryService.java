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

    boolean contains(String assetId);

    /** Baut den Index neu auf und liefert die Anzahl gefundener Bilder. */
    int rescan();
}

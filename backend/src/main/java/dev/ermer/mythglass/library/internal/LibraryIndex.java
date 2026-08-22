package dev.ermer.mythglass.library.internal;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.Folder;
import java.util.List;
import java.util.Map;

/**
 * Unveränderliche Momentaufnahme der Bibliothek. Ein Rescan ersetzt die Instanz als Ganzes, statt
 * bestehende Strukturen zu verändern — damit sieht ein laufender Request immer einen konsistenten
 * Stand, ohne dass irgendwo gesperrt werden muss.
 */
record LibraryIndex(Map<String, Asset> byId, List<Folder> folders) {

    static LibraryIndex empty() {
        return new LibraryIndex(Map.of(), List.of());
    }

    int size() {
        return byId.size();
    }
}

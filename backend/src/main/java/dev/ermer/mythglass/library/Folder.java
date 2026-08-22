package dev.ermer.mythglass.library;

import java.util.List;

/** Ein Unterordner der Bibliothek. Der Ordner ist die Kategorie — es gibt kein eigenes Tagging. */
public record Folder(String name, List<Asset> assets) {}

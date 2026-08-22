package dev.ermer.mythglass.library;

/**
 * Wird veröffentlicht, nachdem der Index neu aufgebaut wurde.
 *
 * <p>Andere Module hängen sich hier ein, statt dass die Bibliothek sie kennt — {@code stage} nutzt
 * das Signal, um Szenen zu leeren, deren Bild inzwischen aus dem Ordner verschwunden ist.
 */
public record LibraryRescanned(int assetCount) {}

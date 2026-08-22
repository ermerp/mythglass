package dev.ermer.mythglass.stage;

/**
 * Es sollte ein Bild gezeigt werden, das die Bibliothek nicht kennt.
 *
 * <p>Der typische Auslöser ist eine Steuerungsoberfläche mit veralteter Bibliotheksliste. Ein klarer
 * Fehler ist hier deutlich besser als ein Monitor, der ohne Erklärung schwarz bleibt.
 */
public class UnknownAssetException extends RuntimeException {

    public UnknownAssetException(String assetId) {
        super("Unbekanntes Bild: " + assetId);
    }
}

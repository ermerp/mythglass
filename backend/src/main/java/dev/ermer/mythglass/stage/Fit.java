package dev.ermer.mythglass.stage;

/** Einpassung eines Bildes in die Fläche des Monitors. */
public enum Fit {
    /** Vollständig sichtbar, mit Rand — die richtige Wahl für Portraits und Artwork. */
    CONTAIN,
    /** Füllt die Fläche, beschneidet die Ränder — passend für Hintergründe und Stimmungsbilder. */
    COVER
}

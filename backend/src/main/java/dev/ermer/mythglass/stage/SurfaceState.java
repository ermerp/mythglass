package dev.ermer.mythglass.stage;

/**
 * Der vollständige Zustand einer Surface.
 *
 * @param connected ob gerade ein Anzeigegerät für diese Surface verbunden ist. Die Steuerung zeigt
 *     das an, damit am Spieltisch sofort auffällt, wenn der Monitor gar nicht mehr zuhört.
 */
public record SurfaceState(
        String id,
        SurfaceType type,
        String displayName,
        boolean connected,
        Scene scene) {}

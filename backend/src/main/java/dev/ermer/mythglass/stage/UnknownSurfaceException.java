package dev.ermer.mythglass.stage;

/** Es wurde eine Surface angesprochen, die nicht konfiguriert ist. */
public class UnknownSurfaceException extends RuntimeException {

    public UnknownSurfaceException(String surfaceId) {
        super("Unbekannte Surface: " + surfaceId);
    }
}

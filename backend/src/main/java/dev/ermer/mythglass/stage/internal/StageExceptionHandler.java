package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.stage.UnknownAssetException;
import dev.ermer.mythglass.stage.UnknownSurfaceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Übersetzt Domänenfehler in HTTP-Antworten, damit der StageService nichts von HTTP wissen muss.
 *
 * <p>Die Meldungen sind für den Spielleiter gedacht, nicht für ein Log: Wenn am Spieltisch etwas nicht
 * funktioniert, soll die Steuerungsoberfläche sagen können, was los ist.
 */
@RestControllerAdvice
class StageExceptionHandler {

    @ExceptionHandler(UnknownSurfaceException.class)
    ProblemDetail unknownSurface(UnknownSurfaceException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Ausgabeziel nicht gefunden");
        return problem;
    }

    @ExceptionHandler(UnknownAssetException.class)
    ProblemDetail unknownAsset(UnknownAssetException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Bild nicht gefunden");
        return problem;
    }
}

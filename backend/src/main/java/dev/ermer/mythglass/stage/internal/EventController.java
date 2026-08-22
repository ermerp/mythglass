package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.stage.StageService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Der einzige Kanal vom Server zu den Clients.
 *
 * <p>Bewusst SSE statt WebSocket: Der Wiederverbindungsversuch steckt im {@code EventSource} des
 * Browsers, und mehr als eine Richtung wird hier nicht gebraucht — Befehle laufen als gewöhnliches
 * REST zurück.
 */
@RestController
class EventController {

    private final StageService stage;

    EventController(StageService stage) {
        this.stage = stage;
    }

    /**
     * @param surface gesetzt von einem Anzeigegerät, das sich als diese Surface meldet. Die
     *     Steuerungsoberfläche lässt den Parameter weg und hört nur zu.
     */
    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@RequestParam(required = false) @Nullable String surface) {
        return stage.subscribe(surface);
    }
}

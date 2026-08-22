package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.stage.StageState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Hält die offenen SSE-Verbindungen und verteilt Zustandsschnappschüsse.
 *
 * <p>Der Broadcaster kennt weder Surfaces noch Szenen — er transportiert nur. Das Wissen darüber, wer
 * verbunden ist und was das für den Zustand bedeutet, liegt im StageService, sonst würden sich beide
 * gegenseitig benötigen.
 */
@Component
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);

    /** Ohne Zeitlimit: Die Verbindung soll die ganze Sitzung halten. Tote Gegenstellen fallen über den Heartbeat auf. */
    private static final long NO_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    SseBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Meldet einen neuen Empfänger an.
     *
     * @param onClose wird genau einmal ausgeführt, wenn die Verbindung endet — egal ob regulär, durch
     *     Zeitablauf oder durch einen Fehler
     */
    public SseEmitter register(Runnable onClose) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        Runnable cleanup = () -> {
            if (emitters.remove(emitter)) {
                onClose.run();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(throwable -> cleanup.run());
        emitters.add(emitter);
        return emitter;
    }

    /**
     * Schickt den Zustand an alle Empfänger.
     *
     * <p>Die Serialisierung passiert hier einmal von Hand statt über die Message-Converter: Im
     * asynchronen Kontext ist das berechenbarer, und der Aufwand fällt nur einmal an statt pro Client.
     */
    public void broadcast(StageState state) {
        if (emitters.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(state);
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("state").data(payload, MediaType.APPLICATION_JSON));
        }
    }

    /**
     * Hält die Verbindungen offen und deckt Gegenstellen auf, die verschwunden sind, ohne sich
     * abzumelden — der Normalfall, wenn das Handy in den Flugmodus geht oder das WLAN wegbricht.
     *
     * <p>Erst der fehlgeschlagene Schreibversuch macht einen solchen Abgang sichtbar; ohne Heartbeat
     * bliebe eine Surface in der Steuerung beliebig lange fälschlich als "verbunden" stehen.
     */
    @Scheduled(fixedRateString = "${mythglass.stage.heartbeat-interval:15s}")
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    /**
     * Beendet alle Ströme, sobald der Kontext schließt.
     *
     * <p>Ohne das zählt jede offene SSE-Verbindung für Tomcat als laufender Request: Das geordnete
     * Herunterfahren wartet dann bis zum Timeout, und ein {@code docker compose restart} auf dem Pi
     * dauert eine halbe Minute statt eines Augenblicks. {@link ContextClosedEvent} ist der richtige
     * Zeitpunkt dafür — er kommt, bevor der Webserver auf freiwerdende Requests zu warten beginnt.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAllOnShutdown() {
        List<SseEmitter> open = List.copyOf(emitters);
        // Erst leeren, dann schließen: So läuft der Abmelde-Callback ins Leere, statt beim
        // Herunterfahren noch einen Zustand an bereits geschlossene Empfänger verteilen zu wollen.
        emitters.clear();
        for (SseEmitter emitter : open) {
            try {
                emitter.complete();
            } catch (RuntimeException alreadyClosed) {
                log.trace("Empfänger war beim Herunterfahren bereits geschlossen.", alreadyClosed);
            }
        }
        log.info("{} SSE-Verbindung(en) beim Herunterfahren geschlossen.", open.size());
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            // Der Empfänger ist weg. completeWithError löst die registrierten Callbacks aus, die ihn
            // aus der Liste nehmen und den Verbindungszähler korrigieren.
            log.debug("SSE-Empfänger nicht mehr erreichbar, wird abgemeldet.", e);
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException alreadyClosed) {
                log.trace("Empfänger war bereits geschlossen.", alreadyClosed);
            }
        }
    }
}

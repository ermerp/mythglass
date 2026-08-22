package dev.ermer.mythglass.stage;

import dev.ermer.mythglass.library.Asset;
import dev.ermer.mythglass.library.LibraryService;
import dev.ermer.mythglass.stage.internal.SseBroadcaster;
import dev.ermer.mythglass.stage.internal.StageProperties;
import dev.ermer.mythglass.stage.internal.StageProperties.SurfaceDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hält den maßgeblichen Zustand aller Ausgabeziele.
 *
 * <p>Die angeschlossenen Geräte haben bewusst kein eigenes Gedächtnis: Sie stellen dar, was ihnen der
 * Server schickt, und fragen bei jeder neuen Verbindung den vollständigen Zustand ab. Damit ist ein
 * Verbindungsabriss — gesperrtes Handy, weggebrochenes WLAN, neu gestarteter Browser — kein Sonderfall,
 * der behandelt werden müsste, sondern derselbe Ablauf wie ein erstmaliges Verbinden.
 *
 * <p>Der Zustand wird nicht persistiert. Nach einem Neustart zeigt jede Surface wieder ihr Ruhebild,
 * und das ist die richtige Vorgabe: Lieber zeigt der Monitor nichts Bestimmtes, als versehentlich das
 * Bild von vorhin.
 */
@Service
public class StageService {

    private static final Logger log = LoggerFactory.getLogger(StageService.class);

    private final Map<String, SurfaceDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<String, Integer> connectionCounts = new ConcurrentHashMap<>();

    private final LibraryService library;
    private final SseBroadcaster broadcaster;
    private final String idleImageName;

    StageService(StageProperties properties, LibraryService library, SseBroadcaster broadcaster) {
        this.library = library;
        this.broadcaster = broadcaster;
        this.idleImageName = properties.idleImage();
        for (SurfaceDefinition definition : properties.surfaces()) {
            definitions.put(definition.id(), definition);
            scenes.put(definition.id(), Scene.BLANK);
        }
        log.info("Surfaces konfiguriert: {}", definitions.keySet());
    }

    public StageState state() {
        List<SurfaceState> surfaces = definitions.values().stream()
                .map(definition -> new SurfaceState(
                        definition.id(),
                        definition.type(),
                        definition.displayName(),
                        connectionCounts.getOrDefault(definition.id(), 0) > 0,
                        scenes.get(definition.id())))
                .toList();
        return new StageState(surfaces, idleAssetId());
    }

    /**
     * Das Bild, das eine Surface zeigt, solange nichts geschaltet ist.
     *
     * <p>Ein schwarzer Monitor beim Start sieht aus, als sei etwas kaputt. Liegt in der Bibliothek
     * ein Bild mit dem konfigurierten Namen, wird es zum Ruhebild; sonst zeigt die Anzeige ein
     * eingebautes, bewusst dezentes Titelbild — hell genug, um zu zeigen, dass alles läuft, und dunkel
     * genug, um einen Raum im Halbdunkel nicht zu erhellen.
     */
    private @Nullable String idleAssetId() {
        return library.findByDisplayName(idleImageName).map(Asset::id).orElse(null);
    }

    /**
     * Stellt eine Szene auf einer Surface dar.
     *
     * @throws UnknownSurfaceException wenn die Surface nicht konfiguriert ist
     * @throws UnknownAssetException wenn die Szene ein Bild nennt, das die Bibliothek nicht kennt
     */
    public void show(String surfaceId, Scene scene) {
        requireSurface(surfaceId);
        if (scene instanceof ImageScene image && !library.contains(image.assetId())) {
            throw new UnknownAssetException(image.assetId());
        }
        scenes.put(surfaceId, scene);
        log.info("Surface {} zeigt jetzt {}", surfaceId, scene);
        broadcast();
    }

    /** Die Panik-Taste. */
    public void blank(String surfaceId) {
        show(surfaceId, Scene.BLANK);
    }

    /**
     * Meldet einen Empfänger für Zustandsänderungen an.
     *
     * @param surfaceId gesetzt, wenn sich ein Anzeigegerät als diese Surface meldet; {@code null} für
     *     reine Beobachter wie die Steuerungsoberfläche
     */
    public SseEmitter subscribe(@Nullable String surfaceId) {
        if (surfaceId != null) {
            requireSurface(surfaceId);
            connectionCounts.merge(surfaceId, 1, Integer::sum);
            log.info("Anzeigegerät für Surface {} verbunden.", surfaceId);
        }
        SseEmitter emitter = broadcaster.register(() -> onDisconnect(surfaceId));
        // Der neue Empfänger ist bereits angemeldet und erhält den Schnappschuss mit — genau das macht
        // aus dem Verbinden eine Synchronisation.
        broadcast();
        return emitter;
    }

    /**
     * Reagiert darauf, dass die Bibliothek neu eingelesen wurde.
     *
     * <p>Zwei Dinge können sich geändert haben: Ein gezeigtes Bild ist verschwunden — dann wird die
     * Surface geleert, statt einen toten Verweis stehen zu lassen. Und das Ruhebild kann dazugekommen
     * oder weggefallen sein. Deshalb wird hier immer verteilt und nicht nur bei geleerten Szenen:
     * Sonst legt jemand ein Titelbild ab, drückt «Neu einlesen» — und auf dem Monitor passiert nichts.
     */
    public void onLibraryChanged() {
        for (Map.Entry<String, Scene> entry : scenes.entrySet()) {
            if (entry.getValue() instanceof ImageScene image && !library.contains(image.assetId())) {
                log.info("Bild {} auf Surface {} ist verschwunden — Surface wird geleert.",
                        image.assetId(), entry.getKey());
                entry.setValue(Scene.BLANK);
            }
        }
        broadcast();
    }

    private void onDisconnect(@Nullable String surfaceId) {
        if (surfaceId != null) {
            connectionCounts.computeIfPresent(surfaceId, (id, count) -> count <= 1 ? null : count - 1);
            log.info("Anzeigegerät für Surface {} getrennt.", surfaceId);
        }
        broadcast();
    }

    private void requireSurface(String surfaceId) {
        if (!definitions.containsKey(surfaceId)) {
            throw new UnknownSurfaceException(surfaceId);
        }
    }

    private void broadcast() {
        broadcaster.broadcast(state());
    }
}

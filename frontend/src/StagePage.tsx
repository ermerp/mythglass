import { useEffect, useState } from "react";
import { imageUrl, type Fit } from "./api";
import { useStageState } from "./useStageState";

interface Layer {
  url: string | null;
  fit: Fit;
  /**
   * Das Bild ließ sich nicht laden. Die Ebene merkt sich trotzdem ihre URL, sonst würde der Effekt
   * unten die Ziel-URL erneut als offen ansehen und es endlos wieder versuchen.
   */
  broken?: boolean;
}

const EMPTY: Layer = { url: null, fit: "CONTAIN" };

/**
 * Was auf dem Monitor hinter dem Spielleiterschirm zu sehen ist.
 *
 * Die Seite hat kein eigenes Gedächtnis — sie stellt dar, was der Server schickt. Wird sie neu
 * geladen oder bricht die Verbindung weg, holt sie sich beim nächsten Verbinden den vollständigen
 * Zustand und ist damit wieder synchron.
 */
export function StagePage({ surfaceId }: { surfaceId: string }) {
  const { state } = useStageState(surfaceId);
  const surface = state?.surfaces.find((candidate) => candidate.id === surfaceId);
  const scene = surface?.scene;

  // Ist nichts geschaltet, tritt das Ruhebild an die Stelle des Bildes. Für die Überblendung unten
  // ist das kein Sonderfall, sondern einfach eine andere Adresse.
  const idleUrl = state?.idleAssetId != null ? imageUrl(state.idleAssetId) : null;
  const targetUrl = scene?.type === "image" ? imageUrl(scene.assetId) : idleUrl;
  const targetFit: Fit = scene?.type === "image" ? scene.fit : "CONTAIN";

  // Zwei übereinanderliegende Ebenen: Die neue wird eingeblendet, während die alte verschwindet.
  const [layers, setLayers] = useState<[Layer, Layer]>([EMPTY, EMPTY]);
  const [front, setFront] = useState(0);

  useEffect(() => {
    const current = layers[front];
    // Die Einpassung gehört in den Vergleich: Dasselbe Bild noch einmal zu schalten, nur mit anderer
    // Einpassung, ist eine echte Änderung — ohne diesen Vergleich bliebe sie ohne Wirkung.
    if (current.url === targetUrl && current.fit === targetFit) {
      return;
    }

    let cancelled = false;
    const swapTo = (layer: Layer) => {
      if (cancelled) {
        return;
      }
      const back = 1 - front;
      setLayers((previous) => {
        const next: [Layer, Layer] = [previous[0], previous[1]];
        next[back] = layer;
        return next;
      });
      setFront(back);
    };

    if (targetUrl === null) {
      swapTo({ url: null, fit: targetFit });
      return;
    }

    // Erst laden, dann überblenden. Ohne das würde die Überblendung durch Schwarz laufen, während
    // das Bild noch über das Netz kommt — am Spieltisch sieht das nach einem Fehler aus.
    const preload = new Image();
    preload.addEventListener("load", () => swapTo({ url: targetUrl, fit: targetFit }));
    // Bei einem Ladefehler auf das Ruhebild zurückfallen statt das alte Bild stehen zu lassen: Der
    // Spielleiter hat etwas anderes angeordnet. Und ein Symbol für ein kaputtes Bild ist das
    // Letzte, was die Spieler sehen sollen.
    preload.addEventListener("error", () =>
      swapTo({ url: targetUrl, fit: targetFit, broken: true }),
    );
    preload.src = targetUrl;

    return () => {
      cancelled = true;
    };
  }, [targetUrl, targetFit, front, layers]);

  const unknownSurface = state !== null && surface === undefined;
  const showingImage = layers[front].url !== null && layers[front].broken !== true;

  // Das eingebaute Ruhebild erscheint nur, wenn es kein eigenes gibt — oder wenn sich das eigene
  // nicht laden ließ. Sonst blitzte es beim Hochfahren kurz auf, während das Titelbild noch geladen
  // wird, und würde gleich darauf wieder überblendet.
  const showBuiltInIdle =
    !showingImage && (idleUrl === null || layers[front].broken === true);

  return (
    <div className="stage">
      {/*
        Liegt kein Ruhebild in der Bibliothek, tritt dieses eingebaute an seine Stelle. Es liegt
        hinter den Bildebenen und wird ausgeblendet, sobald eine davon etwas zeigt — sonst stünde es
        bei «Ganz zeigen» in den schwarzen Rändern.
      */}
      <div
        className="stage-idle"
        style={{ opacity: showBuiltInIdle ? 1 : 0 }}
        aria-hidden={!showBuiltInIdle}
      >
        <IdleScreen />
      </div>

      {layers.map((layer, index) => (
        <div
          key={index}
          className="stage-layer"
          style={{ opacity: index === front ? 1 : 0 }}
          aria-hidden={index !== front}
        >
          {layer.url !== null && layer.broken !== true && (
            <img
              src={layer.url}
              alt=""
              style={{ objectFit: layer.fit === "COVER" ? "cover" : "contain" }}
            />
          )}
        </div>
      ))}

      {unknownSurface && (
        <p className="stage-notice">
          Kein Ausgabeziel mit der Kennung <code>{surfaceId}</code>.
          <br />
          In <code>application.yaml</code> unter <code>mythglass.stage.surfaces</code> eintragen.
        </p>
      )}
    </div>
  );
}

/**
 * Das eingebaute Ruhebild.
 *
 * Bewusst sehr dunkel gehalten: Der Monitor steht in einem Raum im Halbdunkel und soll ihn nicht
 * erhellen, während nichts gezeigt wird. Es geht nur darum zu zeigen, dass alles läuft — ein
 * schwarzer Bildschirm sieht aus wie ein Defekt.
 *
 * Als Zeichnung statt als Bilddatei, damit es in jeder Auflösung scharf bleibt und nichts
 * mitausgeliefert werden muss.
 */
function IdleScreen() {
  return (
    <div className="idle-screen">
      <svg className="idle-mark" viewBox="0 0 120 120" aria-hidden="true">
        <circle cx="60" cy="60" r="46" fill="none" stroke="currentColor" strokeWidth="1.2" opacity="0.55" />
        <circle cx="60" cy="60" r="34" fill="none" stroke="currentColor" strokeWidth="0.6" opacity="0.35" />
        <path
          d="M60 22 L92 46 L80 88 L40 88 L28 46 Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinejoin="round"
          opacity="0.7"
        />
        <path d="M60 22 L60 88 M28 46 L92 46" stroke="currentColor" strokeWidth="0.6" opacity="0.3" />
      </svg>
      <p className="idle-name">Mythglass</p>
    </div>
  );
}

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

  const targetUrl = scene?.type === "image" ? imageUrl(scene.assetId) : null;
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
    // Bei einem Ladefehler auf Schwarz gehen statt das alte Bild stehen zu lassen: Der Spielleiter
    // hat etwas anderes angeordnet. Und Schwarz ist auf einem Monitor, den die Spieler sehen,
    // allemal besser als ein Symbol für ein kaputtes Bild.
    preload.addEventListener("error", () =>
      swapTo({ url: targetUrl, fit: targetFit, broken: true }),
    );
    preload.src = targetUrl;

    return () => {
      cancelled = true;
    };
  }, [targetUrl, targetFit, front, layers]);

  const unknownSurface = state !== null && surface === undefined;

  return (
    <div className="stage">
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

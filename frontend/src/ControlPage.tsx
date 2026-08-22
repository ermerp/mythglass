import { useCallback, useEffect, useState } from "react";
import {
  blankSurface,
  fetchLibrary,
  rescanLibrary,
  showScene,
  thumbnailUrl,
  type Fit,
  type Library,
  type SurfaceState,
} from "./api";
import { useStageState } from "./useStageState";

/**
 * Was der Spielleiter in der Hand hält.
 *
 * Die Bedienung ist auf eine Hand im Halbdunkel ausgelegt, während nebenbei erzählt wird: große
 * Kacheln, kurze Wege, und ein Schwarz-Knopf, der immer sichtbar bleibt. Was gerade auf dem Monitor
 * steht, zeigt die Oberfläche oben an — damit niemand sich umdrehen muss, um das zu prüfen.
 */
export function ControlPage() {
  const { state, online } = useStageState();
  const [library, setLibrary] = useState<Library | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedSurfaceId, setSelectedSurfaceId] = useState<string | null>(null);
  const [fit, setFit] = useState<Fit>("CONTAIN");

  const surfaces = state?.surfaces ?? [];
  const surface =
    surfaces.find((candidate) => candidate.id === selectedSurfaceId) ?? surfaces[0];

  const reloadLibrary = useCallback(async () => {
    try {
      setLibrary(await fetchLibrary());
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, []);

  useEffect(() => {
    void reloadLibrary();
  }, [reloadLibrary]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  };

  if (surface === undefined) {
    return (
      <main className="control">
        <p className="notice">{online ? "Keine Ausgabeziele konfiguriert." : "Verbinde mit Mythglass …"}</p>
      </main>
    );
  }

  const shownAssetId = surface.scene.type === "image" ? surface.scene.assetId : null;

  return (
    <main className="control">
      <header className="control-header">
        <div className="status-row">
          <ConnectionBadge online={online} surface={surface} />
          {surfaces.length > 1 && (
            <div className="surface-picker">
              {surfaces.map((candidate) => (
                <button
                  key={candidate.id}
                  type="button"
                  className={candidate.id === surface.id ? "chip chip-active" : "chip"}
                  onClick={() => setSelectedSurfaceId(candidate.id)}
                >
                  {candidate.displayName}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="now-showing">
          <div className="now-showing-preview">
            {shownAssetId !== null ? (
              <img src={thumbnailUrl(shownAssetId)} alt="" />
            ) : (
              <span className="now-showing-empty">schwarz</span>
            )}
          </div>
          <div className="now-showing-text">
            <span className="label">Auf dem Monitor</span>
            <strong>{describe(surface, library)}</strong>
          </div>
          <button
            type="button"
            className="blank-button"
            disabled={busy}
            onClick={() => void run(() => blankSurface(surface.id))}
          >
            Schwarz
          </button>
        </div>

        {error !== null && <p className="error">{error}</p>}
      </header>

      <div className="toolbar">
        <div className="fit-picker" role="group" aria-label="Einpassung">
          {(["CONTAIN", "COVER"] as const).map((option) => (
            <button
              key={option}
              type="button"
              className={option === fit ? "chip chip-active" : "chip"}
              onClick={() => setFit(option)}
            >
              {option === "CONTAIN" ? "Ganz zeigen" : "Fläche füllen"}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="chip"
          disabled={busy}
          onClick={() =>
            void run(async () => {
              await rescanLibrary();
              await reloadLibrary();
            })
          }
        >
          Neu einlesen
        </button>
      </div>

      {library === null ? (
        <p className="notice">Bibliothek wird geladen …</p>
      ) : library.folders.length === 0 ? (
        <p className="notice">
          Die Bibliothek ist leer. Bilder in den Bibliotheksordner legen und «Neu einlesen» drücken.
        </p>
      ) : (
        library.folders.map((folder) => (
          <section key={folder.name} className="folder">
            <h2>{folder.name}</h2>
            <div className="grid">
              {folder.assets.map((asset) => (
                <button
                  key={asset.id}
                  type="button"
                  className={asset.id === shownAssetId ? "tile tile-active" : "tile"}
                  disabled={busy}
                  onClick={() =>
                    void run(() =>
                      showScene(surface.id, { type: "image", assetId: asset.id, fit }),
                    )
                  }
                >
                  <img src={thumbnailUrl(asset.id)} alt="" loading="lazy" />
                  <span className="tile-name">{asset.name}</span>
                </button>
              ))}
            </div>
          </section>
        ))
      )}
    </main>
  );
}

function ConnectionBadge({ online, surface }: { online: boolean; surface: SurfaceState }) {
  if (!online) {
    return <span className="badge badge-bad">Keine Verbindung zum Server</span>;
  }
  return surface.connected ? (
    <span className="badge badge-good">{surface.displayName} verbunden</span>
  ) : (
    <span className="badge badge-warn">{surface.displayName} nicht verbunden</span>
  );
}

function describe(surface: SurfaceState, library: Library | null): string {
  if (surface.scene.type === "blank") {
    return "Nichts";
  }
  const assetId = surface.scene.assetId;
  const asset = library?.folders.flatMap((folder) => folder.assets).find((candidate) => candidate.id === assetId);
  return asset?.name ?? "Unbekanntes Bild";
}

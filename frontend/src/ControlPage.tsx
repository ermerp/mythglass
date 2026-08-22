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
 * Kacheln, kurze Wege, und ein Panik-Knopf, der immer sichtbar bleibt. Alles, was man selten
 * anfasst, liegt hinter dem Zahnrad — der Platz über dem Trennstrich gehört den Bildern.
 */
export function ControlPage() {
  const { state, online } = useStageState();
  const [library, setLibrary] = useState<Library | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [selectedSurfaceId, setSelectedSurfaceId] = useState<string | null>(null);
  const [fit, setFit] = useState<Fit>("CONTAIN");

  const surfaces = state?.surfaces ?? [];
  const surface = surfaces.find((candidate) => candidate.id === selectedSurfaceId) ?? surfaces[0];

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
        <p className="notice">
          {online ? "Keine Ausgabeziele konfiguriert." : "Verbinde mit Mythglass …"}
        </p>
      </main>
    );
  }

  const shownAssetId = surface.scene.type === "image" ? surface.scene.assetId : null;
  // Was tatsächlich auf dem Monitor steht: das geschaltete Bild, sonst das Ruhebild. Die Vorschau
  // soll die Wahrheit zeigen, damit sich niemand umdrehen muss.
  const idleAssetId = state?.idleAssetId ?? null;
  const previewAssetId = shownAssetId ?? idleAssetId;

  /**
   * Die Einpassung gilt für den nächsten Griff — läuft aber gerade ein Bild, wird sie sofort darauf
   * angewandt. Sonst müsste man die Kachel erneut suchen und antippen, nur um zu sehen, was die
   * andere Einstellung bewirkt.
   */
  const changeFit = (next: Fit) => {
    setFit(next);
    if (shownAssetId !== null) {
      void run(() => showScene(surface.id, { type: "image", assetId: shownAssetId, fit: next }));
    }
  };

  return (
    <main className="control">
      <header className="control-header">
        <div className="status-row">
          <ConnectionBadge online={online} surface={surface} />
          <div className="header-actions">
            {surfaces.length > 1 &&
              surfaces.map((candidate) => (
                <button
                  key={candidate.id}
                  type="button"
                  className={candidate.id === surface.id ? "chip chip-active" : "chip"}
                  onClick={() => setSelectedSurfaceId(candidate.id)}
                >
                  {candidate.displayName}
                </button>
              ))}
            <button
              type="button"
              className={settingsOpen ? "icon-button icon-button-active" : "icon-button"}
              aria-label="Einstellungen"
              aria-expanded={settingsOpen}
              onClick={() => setSettingsOpen((open) => !open)}
            >
              <GearIcon />
            </button>
          </div>
        </div>

        <div className="now-showing">
          <div className="now-showing-preview">
            {previewAssetId !== null ? (
              <img src={thumbnailUrl(previewAssetId)} alt="" />
            ) : (
              <span className="now-showing-empty">Ruhe</span>
            )}
          </div>
          <div className="now-showing-text">
            <span className="label">Auf dem Monitor</span>
            <strong>{describe(surface, library, idleAssetId !== null)}</strong>
          </div>
          <button
            type="button"
            className="blank-button"
            disabled={busy}
            onClick={() => void run(() => blankSurface(surface.id))}
          >
            Panik!
          </button>
        </div>

        {settingsOpen && (
          <div className="settings-panel">
            <div className="setting">
              <span className="setting-label">Einpassung</span>
              <div className="fit-picker" role="group" aria-label="Einpassung">
                {(["CONTAIN", "COVER"] as const).map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={option === fit ? "chip chip-active" : "chip"}
                    disabled={busy}
                    onClick={() => changeFit(option)}
                  >
                    {option === "CONTAIN" ? "Ganz zeigen" : "Fläche füllen"}
                  </button>
                ))}
              </div>
            </div>

            <div className="setting">
              <span className="setting-label">Bibliothek</span>
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

            <a className="settings-link" href="/">
              Alle Bildschirme
            </a>
          </div>
        )}

        {error !== null && <p className="error">{error}</p>}
      </header>

      {library === null ? (
        <p className="notice">Bibliothek wird geladen …</p>
      ) : library.folders.length === 0 ? (
        <p className="notice">
          Die Bibliothek ist leer. Bilder in den Bibliotheksordner legen und unter dem Zahnrad «Neu
          einlesen» drücken.
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
                    void run(() => showScene(surface.id, { type: "image", assetId: asset.id, fit }))
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

function describe(surface: SurfaceState, library: Library | null, hasIdleImage: boolean): string {
  if (surface.scene.type === "blank") {
    return hasIdleImage ? "Ruhebild" : "Nichts";
  }
  const assetId = surface.scene.assetId;
  const asset = library?.folders
    .flatMap((folder) => folder.assets)
    .find((candidate) => candidate.id === assetId);
  return asset?.name ?? "Unbekanntes Bild";
}

function GearIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3.2" />
      <path d="M19.4 14.5a1.6 1.6 0 0 0 .33 1.78l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.6 1.6 0 0 0-1.78-.33 1.6 1.6 0 0 0-.98 1.47V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1.05-1.47 1.6 1.6 0 0 0-1.78.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.6 1.6 0 0 0 .33-1.78 1.6 1.6 0 0 0-1.47-.98H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.47-1.05 1.6 1.6 0 0 0-.33-1.78l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.6 1.6 0 0 0 1.78.33H9a1.6 1.6 0 0 0 .98-1.47V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 .98 1.47 1.6 1.6 0 0 0 1.78-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.6 1.6 0 0 0-.33 1.78V9a1.6 1.6 0 0 0 1.47.98H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.47.98z" />
    </svg>
  );
}

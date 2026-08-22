import type { SurfaceState } from "./api";
import { useStageState } from "./useStageState";

/**
 * Die Startseite: eine Adresse, hinter der alle Bildschirme liegen.
 *
 * Die Felder für die Ausgabeziele entstehen aus der Konfiguration des Servers, nicht aus einer Liste
 * hier — ein weiterer Eintrag unter `mythglass.stage.surfaces` erscheint hier von selbst. Was später
 * dazukommt (Soundboard, Oberflächen für einzelne Spieler), ist ein weiterer Abschnitt auf dieser
 * Seite und keine neue Adresse, die sich jemand merken müsste.
 */
export function HubPage() {
  const { state, online } = useStageState();

  return (
    <main className="hub">
      <header className="hub-header">
        <h1>Mythglass</h1>
        <p>{online ? "Wohin möchtest du?" : "Verbinde mit dem Server …"}</p>
      </header>

      <section className="hub-section">
        <h2>Spielleitung</h2>
        <a className="hub-card hub-card-primary" href="/control">
          <span className="hub-card-icon" aria-hidden="true">
            <ControlIcon />
          </span>
          <span className="hub-card-text">
            <strong>Steuerung</strong>
            <span className="hub-card-detail">Bilder auf die Bildschirme schalten</span>
          </span>
        </a>
      </section>

      <section className="hub-section">
        <h2>Anzeigen</h2>
        {state === null ? (
          <p className="notice">Wird geladen …</p>
        ) : (
          state.surfaces.map((surface) => <SurfaceCard key={surface.id} surface={surface} />)
        )}
      </section>
    </main>
  );
}

/**
 * Ein Ausgabeziel. Öffnet in einem neuen Tab, weil man diese Seiten einrichtet und dann stehen lässt
 * — die Übersicht soll dabei erreichbar bleiben.
 */
function SurfaceCard({ surface }: { surface: SurfaceState }) {
  const href = `/stage/${encodeURIComponent(surface.id)}`;
  return (
    <a className="hub-card" href={href} target="_blank" rel="noreferrer">
      <span className="hub-card-icon" aria-hidden="true">
        <MonitorIcon />
      </span>
      <span className="hub-card-text">
        <strong>{surface.displayName}</strong>
        <span className="hub-card-detail">{href}</span>
      </span>
      <span className={surface.connected ? "dot dot-on" : "dot"} title={surface.connected ? "verbunden" : "nicht verbunden"}>
        <span className="visually-hidden">{surface.connected ? "verbunden" : "nicht verbunden"}</span>
      </span>
    </a>
  );
}

function ControlIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
      <path d="M4 6h16M4 12h16M4 18h16" />
      <circle cx="9" cy="6" r="2.2" fill="currentColor" stroke="none" />
      <circle cx="15" cy="12" r="2.2" fill="currentColor" stroke="none" />
      <circle cx="7" cy="18" r="2.2" fill="currentColor" stroke="none" />
    </svg>
  );
}

function MonitorIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round">
      <rect x="2.5" y="4" width="19" height="13" rx="1.5" />
      <path d="M9 20.5h6M12 17v3.5" strokeLinecap="round" />
    </svg>
  );
}

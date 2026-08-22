# Mythglass

Präsentationswerkzeug für Pen-and-Paper-Runden vor Ort. Der Spielleiter zeigt den Spielern Bilder von
NPCs, Gegnern und Orten auf einem Monitor am Spielleiterschirm und steuert das vom Handy oder Tablet.

Regelwerkunabhängig — die Software weiß nichts von Klassen, Würfeln oder Trefferpunkten.

## Der Grundgedanke

Der Kern ist nicht «ein Bild auf einen Monitor schicken», sondern **Zustand auf benannte Ausgabeziele
projizieren**:

- **Surface** — ein Ausgabeziel (`main`, später `table-map`), in der Konfiguration deklariert
- **Scene** — was auf einer Surface dargestellt wird (`blank`, `image`, später Karte und Klang)
- **Der Server hält den maßgeblichen Zustand.** Anzeigegerät und Steuerung haben kein eigenes
  Gedächtnis; beim Verbinden bekommen sie den vollständigen Zustand geschickt

Der letzte Punkt ist keine Ästhetik, sondern die Fehlerbehandlung. Gesperrtes Handy, weggebrochenes
WLAN, neu geladener Browser: Jede Wiederverbindung ist derselbe Ablauf wie die erste Verbindung, und
danach ist alles wieder synchron. Es gibt keinen Nachholmechanismus, den man vergessen könnte.

Deshalb ist auch der zweite Monitor später kein Umbau, sondern ein Eintrag in `application.yaml` plus
ein zweites Browserfenster.

## Architektur

```
  Handy/Tablet                 Raspberry Pi                          Monitor
  ┌──────────┐        ┌──────────────────────────────┐        ┌──────────────┐
  │ Control  │  REST  │  Docker: Spring Boot         │        │  Chromium    │
  │   UI     │───────▶│   ├─ library (Ordner-Index)  │◀── SSE─│   --kiosk    │
  │ (React)  │◀── SSE─│   └─ stage   (Zustand)       │  REST  │ /stage/main  │
  └──────────┘        │  static/ = React-Build       │───────▶│  (React)     │
                      └──────────────────────────────┘        └──────────────┘
                         ▲ read-only Bind-Mount
                      /srv/mythglass/library  ◀── Samba ── Laptop des Spielleiters
```

**SSE statt WebSocket:** Der Wiederverbindungsversuch steckt im `EventSource` des Browsers, und mehr
als eine Richtung wird nicht gebraucht — Befehle laufen als gewöhnliches REST zurück.

**Keine Datenbank.** Der Ordner *ist* die Bibliothek: Unterordner sind Kategorien, Dateinamen
Anzeigenamen. Der Index lebt im Speicher und wird beim Start sowie auf Anforderung neu aufgebaut. Das
spart JPA, Migrationen und die Frage, welche Datenbank auf einem Pi Sinn ergibt.

## Aufbau

```
backend/     Spring Boot 4, Java 21, Spring Modulith
  library/   Ordner-Index, Vorschaubilder, Auslieferung der Bilder
  stage/     Surfaces, Szenen, maßgeblicher Zustand, Verteilung über SSE
frontend/    React 19 + TypeScript, per Vite gebaut, landet im Jar
docs/        Einrichtung des Raspberry Pi
scripts/     Einrichtung des Vollbild-Browsers
```

`library` kennt `stage` nicht. Die Bibliothek meldet nur, dass sie neu eingelesen wurde; die Bühne
entscheidet selbst, was das für sie bedeutet — sie leert Szenen, deren Bild verschwunden ist. Die
Modulgrenzen sind nicht bloß Absicht, sondern werden von `ModularityTest` erzwungen.

Die Naht, an der die Anwendung wächst, ist `Scene`: ein `sealed interface` mit einem Record je
Inhaltstyp. Ein neuer Typ ist ein Record hier plus eine Darstellung im Frontend, die auf das
`type`-Feld matched. Am Zustandsmodell, an der Verteilung und an der Steuerungs-API ändert sich nichts.

## Entwickeln

Voraussetzungen: JDK 21. Node wird vom Gradle-Build selbst geladen.

```bash
# Backend samt gebauter Oberfläche
./gradlew :backend:bootRun

# Oberfläche mit Hot Reload (Backend muss daneben laufen)
cd frontend && npm run dev
```

Ein paar Bilder nach `data/library/` legen — Unterordner werden zu Kategorien. Dann:

- Steuerung: <http://localhost:8080>
- Anzeige: <http://localhost:8080/stage/main>

Am besten in zwei Fenstern nebeneinander.

```bash
./gradlew :backend:test    # alle Tests
./gradlew :backend:bootJar # ein Jar mit allem darin
```

## API

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/library` | Ordner mit ihren Bildern |
| `POST` | `/api/library/rescan` | Index neu aufbauen |
| `GET` | `/api/assets/{id}/thumb` | Vorschaubild (JPEG, lange Kante 480 px) |
| `GET` | `/api/assets/{id}/full` | Originaldatei |
| `GET` | `/api/surfaces` | Zustand aller Ausgabeziele |
| `PUT` | `/api/surfaces/{id}/scene` | Szene setzen |
| `POST` | `/api/surfaces/{id}/blank` | Schwarz schalten |
| `GET` | `/api/events[?surface={id}]` | Zustandsstrom (SSE) |

`/api/events` schickt bei jeder Verbindung zuerst den vollständigen Zustand. Mit `surface` meldet
sich ein Anzeigegerät als dieses Ausgabeziel und erscheint in der Steuerung als «verbunden».

## Auf dem Pi

Siehe [docs/pi-setup.md](docs/pi-setup.md).

## Was v1 bewusst nicht kann

Kein Login (reines LAN-Werkzeug), kein Upload über die Weboberfläche, keine Datenbank, kein Tagging,
keine Kampagnenverwaltung, kein Plugin-System, kein Sound, keine Karte, kein Video.

«Erweiterbar» heißt hier saubere Nähte, nicht Infrastruktur für Erweiterungen, die noch niemand
geschrieben hat.

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
e2e/         Playwright: prüft beide Geräte gleichzeitig gegen das gebaute Jar
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

Es gibt drei Schleifen, von schnell nach gründlich. Der Pi ist keine davon — er ist die
Abnahmeumgebung, nicht die Werkbank.

### 1. Innere Schleife — Sekunden

Für alles, was an der Oberfläche passiert:

```bash
./gradlew :backend:bootRun          # Backend auf 8080
cd frontend && npm run dev          # Oberfläche auf 5173, mit Hot Reload
```

Vite reicht `/api` an 8080 weiter. Bilder nach `data/library/` legen — Unterordner werden zu
Kategorien, beliebig tief verschachtelt. <http://localhost:5173> ist die Übersicht; von dort führt je
ein Feld zur Steuerung und zu den Ausgabezielen. Am besten Steuerung und Bühne nebeneinander öffnen,
dann sieht man beide Geräte auf einem Bildschirm.

| Seite | Adresse |
|---|---|
| Übersicht | `/` |
| Spielleiter-Steuerung | `/control` |
| Ausgabeziel | `/stage/{id}`, in v1 `/stage/main` |

### 2. Zusammenbau — eine Minute

```bash
./gradlew build                     # baut alles, führt die Backend-Tests aus
cd e2e && npx playwright test        # fährt das Jar hoch und prüft beide Geräte im Browser
```

Die E2E-Tests starten das gebaute Jar selbst, legen sich eine eigene Bibliothek an und bedienen
Monitor und Handy als zwei getrennte Browser-Kontexte. Damit ist genau der Stand geprüft, der später
im Container landet.

Sie laufen dabei auf Port 8099 und benutzen nie einen bereits laufenden Server mit — ein `bootRun`
auf 8080 darf also nebenher weiterlaufen, ohne das Ergebnis zu verfälschen.

Einmalig vorab: `cd e2e && npm install && npx playwright install chromium`.

### 3. Container — nur wenn es um den Pi geht

```bash
MYTHGLASS_LIBRARY=./data/library MYTHGLASS_CACHE=./data/cache docker compose up --build
```

Dasselbe Compose-File läuft auf dem Pi ohne die beiden Variablen; die Vorgaben zeigen dort auf
`/srv/mythglass`.

### Vom Handy aus ausprobieren

Unter WSL2 hängt die Anwendung standardmäßig hinter einem eigenen Netz und ist vom Handy aus nicht
erreichbar. Der bequemste Weg ist der gespiegelte Netzwerkmodus — in `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
networkingMode=mirrored
```

Danach `wsl --shutdown` und neu starten. Der Pi ist dann unter der IP des Windows-Rechners
erreichbar, ohne Portweiterleitung.

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

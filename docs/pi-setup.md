# Mythglass auf dem Raspberry Pi einrichten

Vorausgesetzt: ein Pi 4 oder 5 mit Raspberry Pi OS (64 Bit, **Desktop-Variante** — die Lite-Variante
hat keine Oberfläche, in der ein Browser starten könnte), am Monitor hinter dem Spielleiterschirm
angeschlossen und im WLAN des Spielorts.

Die Aufgabenteilung: Der **Container** bringt die Anwendung mit, der **Pi-Host** startet den
Vollbild-Browser. Ein Browser mit Bildschirm- und Grafikzugriff im Container wäre viel Konfiguration
ohne Gegenwert.

## 1. Erreichbarkeit

Das Steuergerät soll keine IP-Adresse tippen müssen. Auf Raspberry Pi OS ist Avahi vorinstalliert,
damit genügt ein passender Hostname:

```bash
sudo hostnamectl set-hostname mythglass
```

Danach ist der Pi im selben Netz unter `mythglass.local` erreichbar. Handy und Tablet müssen im
gleichen WLAN hängen.

**Notiere dir trotzdem die IP-Adresse:**

```bash
hostname -I
```

Nicht jedes Handy und nicht jedes WLAN löst `.local`-Namen auf — manche Router und manche
Android-Stände blockieren das. Wenn die Anwendung vom Handy aus nicht erreichbar ist, probiere immer
zuerst die IP (`http://192.168.…:8080`). Klappt es damit, liegt es am Namen und nicht an Mythglass.

> Wenn ihr an wechselnden Orten spielt und euch das fremde WLAN irgendwann auf die Nerven geht: Der
> Pi kann stattdessen ein eigenes WLAN aufspannen. Das ist ein eigener Einrichtungsschritt und für
> den Anfang bewusst nicht Teil dieser Anleitung.

## 2. Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Danach die Sitzung beenden und neu anmelden — über SSH also ausloggen und neu verbinden, sonst greift
die Gruppenzugehörigkeit nicht und jedes `docker`-Kommando verlangt `sudo`.

## 3. Ordner anlegen

```bash
sudo mkdir -p /srv/mythglass/library /srv/mythglass/cache
sudo chown -R "$USER:$USER" /srv/mythglass
```

`library` enthält die Bilder, `cache` die erzeugten Vorschaubilder. Der Cache darf jederzeit gelöscht
werden — er baut sich beim nächsten Einlesen neu auf.

## 4. Bilder auf den Pi bekommen

Die Anwendung hat bewusst keinen Upload: Der Ordner ist die Bibliothek. Für den Zugriff vom Laptop
eine Netzwerkfreigabe einrichten:

```bash
sudo apt install -y samba
sudo tee -a /etc/samba/smb.conf > /dev/null <<'SMB'

[mythglass]
   path = /srv/mythglass/library
   browseable = yes
   read only = no
   guest ok = no
SMB
sudo smbpasswd -a "$USER"
sudo systemctl restart smbd
```

Die Freigabe liegt dann unter `\\mythglass\mythglass` (Windows) beziehungsweise
`smb://mythglass.local/mythglass` (macOS, Linux). Angemeldet wird sich mit dem Pi-Benutzer und dem
Passwort, das `smbpasswd` gerade gesetzt hat — das ist nicht zwangsläufig das Login-Passwort des Pi.

**Struktur:** Unterordner sind die Kategorien, Dateinamen die Anzeigenamen:

```
library/
├── NPCs/
│   ├── Gorak der Wirt.png
│   └── Elenya.jpg
├── Orte/
│   └── Taverne zum Krummen Ast.jpg
└── Titelbild.jpg
```

Unterordner dürfen beliebig tief verschachtelt sein; sie erscheinen dann flach als `Orte/Stadt/Hafen`.
Ordner ohne eigene Bilder tauchen nicht auf.

Unterstützt werden JPEG, PNG, GIF und BMP. **WebP und AVIF nicht** — dafür fehlt der Standard-Java-
Bildverarbeitung die Unterstützung, und eine Kachel, die mitten in der Sitzung schwarz bleibt, wäre
schlimmer als eine Datei, die gar nicht erst auftaucht. Solche Dateien werden beim Einlesen im
Protokoll gemeldet.

## 5. Anwendung starten

```bash
git clone https://github.com/ermerp/mythglass.git ~/mythglass
cd ~/mythglass
docker compose up -d --build
```

**Nimm dir für diesen Schritt Zeit.** Der Build lädt Gradle, Node und alle Abhängigkeiten und
übersetzt beides auf dem Pi; auf einem PC dauert das gut zwei Minuten, auf einem Pi ein Vielfaches
davon. Zwei Dinge, die ihn scheitern lassen können:

- **Arbeitsspeicher.** Der Gradle- und der Vite-Build zusammen sind hungrig. Auf einem Pi mit 2 GB
  kann der Build ohne Auslagerungsdatei abbrechen — bricht er mit „Killed" oder einem
  OutOfMemoryError ab, ist das die Ursache.
- **Plattenplatz.** Rechne mit rund 3 GB für Gradle-Cache, Node und die Image-Ebenen zusammen.

Jeder weitere Build ist dank des Gradle-Cache deutlich kürzer.

Prüfen, ob die Anwendung läuft:

```bash
curl -s localhost:8080/api/surfaces
docker compose logs -f mythglass
```

Im Protokoll sollte stehen, wie viele Bilder eingelesen wurden.

## 6. Vollbild-Browser einrichten

```bash
./scripts/setup-kiosk.sh
```

Das legt einen Autostart-Eintrag an und schaltet den Bildschirmschoner ab. Nach dem nächsten Neustart
startet der Pi direkt in die Anzeige. Ohne Neustart ausprobieren — je nach Pi-OS-Stand heißt das
Programm `chromium` oder `chromium-browser`:

```bash
chromium --kiosk http://localhost:8080/stage/main
```

## 7. Bedienen

Am Handy oder Tablet **`http://mythglass.local:8080`** öffnen. Das ist die Übersicht mit je einem Feld
pro Bildschirm; ein grüner Punkt zeigt, wo gerade ein Gerät hängt.

Fürs Spielen willst du direkt in die Steuerung: **`http://mythglass.local:8080/control`** — die auf
den Startbildschirm legen, das spart am Spieltisch einen Griff.

| Seite | Adresse | Wofür |
|---|---|---|
| Übersicht | `/` | Einrichten, nachsehen was verbunden ist |
| Steuerung | `/control` | Das Lesezeichen des Spielleiters |
| Anzeige | `/stage/main` | Läuft im Kiosk auf dem Pi |

## Die erste Probe

Nimm dir dafür zwanzig Minuten, bevor ihr wirklich spielt. Der Reihe nach:

1. **Bild schalten.** In der Steuerung eine Kachel antippen — auf dem Monitor erscheint das Bild mit
   einer weichen Überblendung. Die Kachel bekommt einen goldenen Rahmen, oben steht der Name.
2. **Schwarz.** Der Knopf oben rechts leert den Monitor sofort. Das ist die Panik-Taste; probier sie
   aus, damit dein Spielleiter sie im Ernstfall blind trifft.
3. **Einpassung.** Unter dem Zahnrad zwischen „Ganz zeigen" und „Fläche füllen" wechseln, während ein
   Bild läuft. Der Monitor ändert sich sofort. Für Portraits ist „Ganz zeigen" richtig, für
   Stimmungsbilder eher „Fläche füllen".
4. **Handy sperren und wieder entsperren.** Danach muss die Steuerung ohne Neuladen weiterarbeiten
   und weiterhin anzeigen, was auf dem Monitor steht.
5. **WLAN kurz aus und wieder an.** Dasselbe Ergebnis erwartet: Die Steuerung meldet kurz „Keine
   Verbindung zum Server" und fängt sich von selbst wieder.
6. **Pi neu starten.** Er soll von allein in die Anzeige booten und Schwarz zeigen.
7. **Ein Bild nachlegen.** Über die Freigabe eine Datei in einen Ordner kopieren, dann unter dem
   Zahnrad «Neu einlesen» drücken — sie erscheint in der Liste.
8. **Ein großes Bild schalten** und mitzählen, wie lange es bis zur Anzeige dauert. Ruckelt oder
   hängt es spürbar, sag Bescheid: Dann lohnt es sich, die Vollbilder serverseitig zu verkleinern.

Die Punkte 4 und 5 sind die wichtigsten. Sie prüfen die Eigenschaft, auf der der ganze Entwurf
aufbaut — dass der Server den maßgeblichen Zustand hält und sich jedes Gerät beim Verbinden von
selbst wieder abgleicht.

## Betrieb

| Was | Wie |
|---|---|
| Neue Bilder abgelegt | In der Steuerung unter dem Zahnrad «Neu einlesen» drücken |
| Protokoll ansehen | `docker compose logs -f mythglass` |
| Nach Codeänderung aktualisieren | `git pull && docker compose up -d --build` |
| Neu starten | `docker compose restart` |
| Vorschaubilder verwerfen | `rm -rf /srv/mythglass/cache/thumbs` und neu einlesen |

## Wenn etwas nicht funktioniert

**Vom Handy aus gar nichts erreichbar.** Zuerst die IP statt des Namens probieren
(`http://192.168.…:8080`, siehe Schritt 1). Klappt das, löst dein Handy `.local` nicht auf — dann
nimm die IP ins Lesezeichen. Klappt auch das nicht, hängt das Handy in einem anderen Netz.

**Der Monitor bleibt schwarz, die Steuerung meldet «nicht verbunden».** Der Browser auf dem Pi läuft
nicht oder zeigt die falsche Adresse. Prüfen mit `curl -s localhost:8080/api/surfaces` — steht dort
`"connected": false`, hat sich kein Anzeigegerät gemeldet.

**Die Steuerung meldet «Keine Verbindung zum Server».** Der Container läuft nicht, oder das Handy ist
in einem anderen Netz. `docker compose ps` und die WLAN-Einstellungen des Handys prüfen.

**Ein Bild fehlt in der Liste.** Format prüfen (siehe Schritt 4) und ins Protokoll schauen — nicht
unterstützte Dateien werden dort mit ihrer Endung gemeldet.

**Ein ausgetauschtes Bild zeigt noch das alte Motiv.** «Neu einlesen» drücken. Die Anwendung erkennt
den Austausch an Änderungszeit und Größe der Datei.

**Der Build bricht ab.** Siehe Schritt 5 — meistens Arbeitsspeicher oder Plattenplatz.

## Später: der zweite Monitor

Der Tischmonitor mit der Karte braucht keine neue Architektur, sondern zwei Handgriffe:

1. In `backend/src/main/resources/application.yaml` unter `mythglass.stage.surfaces` einen weiteren
   Eintrag anlegen, etwa mit der Kennung `table-map`.
2. `./scripts/setup-kiosk.sh table-map` aufrufen und das Fenster auf den zweiten Bildschirm legen.

Auf der Übersicht erscheint das neue Ausgabeziel von selbst, und die Steuerung zeigt dann eine
Auswahl zwischen den Zielen an.

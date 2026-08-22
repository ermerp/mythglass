# Mythglass auf dem Raspberry Pi einrichten

Diese Anleitung führt von einem frisch aufgesetzten Raspberry Pi bis zu einer laufenden Anzeige, die
vom Handy gesteuert wird. Sie ist von oben nach unten gedacht — jeder Schritt setzt den vorigen
voraus.

**Was du brauchst:**

- Raspberry Pi 4 oder 5 mit Raspberry Pi OS (64 Bit, **Desktop-Variante** — die Lite-Variante hat
  keine Oberfläche, in der ein Browser starten könnte)
- Den Pi im WLAN des Spielorts, am Monitor hinter dem Spielleiterschirm angeschlossen
- Etwa 3 GB freien Platz auf der Karte und Geduld für den ersten Build
- Ein Handy oder Tablet im selben WLAN

**Die Aufgabenteilung:** Der **Container** bringt die Anwendung mit, der **Pi-Host** startet den
Vollbild-Browser. Ein Browser mit Bildschirm- und Grafikzugriff im Container wäre viel Konfiguration
ohne Gegenwert.

---

## 1. Auf den Pi kommen

Alle folgenden Befehle laufen in einem Terminal **auf dem Pi**. Zwei Wege dorthin:

**Direkt am Pi** — Tastatur anschließen und das Terminal in der Oberfläche öffnen.

**Über SSH vom Rechner aus** — bequemer, weil du die Befehle von hier kopieren kannst. Falls SSH noch
nicht an ist, einmalig am Pi selbst:

```bash
sudo raspi-config nonint do_ssh 0
```

Danach vom Windows-Rechner (PowerShell oder Terminal) beziehungsweise aus WSL heraus:

```bash
ssh pi@raspberrypi.local
```

`pi` durch deinen Benutzernamen ersetzen und `raspberrypi` durch den aktuellen Hostnamen des Pi.
Findest du ihn nicht, hilft am Pi selbst `hostname -I` — dann verbindest du dich über die IP.

## 2. Grundausstattung

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y git
```

Das Update dauert beim ersten Mal ein paar Minuten. `git` brauchst du, um Mythglass zu holen und
später zu aktualisieren.

## 3. Namen und Adresse festlegen

Das Steuergerät soll keine IP-Adresse tippen müssen. Auf Raspberry Pi OS ist Avahi vorinstalliert,
damit genügt ein passender Hostname:

```bash
sudo raspi-config nonint do_hostname mythglass
sudo reboot
```

> Bewusst über `raspi-config` und nicht über `hostnamectl`: Letzteres ändert nur den Hostnamen, lässt
> aber den alten Eintrag in `/etc/hosts` stehen. Danach wirft jedes `sudo` eine Warnung und braucht
> spürbar länger.

Nach dem Neustart ist der Pi unter `mythglass.local` erreichbar, und du verbindest dich künftig mit
`ssh pi@mythglass.local`.

**Notiere dir trotzdem die IP-Adresse:**

```bash
hostname -I
```

Nicht jedes Handy und nicht jedes WLAN löst `.local`-Namen auf — manche Router und manche
Android-Stände blockieren das. Wenn die Anwendung vom Handy aus später nicht erreichbar ist, probiere
immer zuerst die IP (`http://192.168.…:8080`). Klappt es damit, liegt es am Namen und nicht an
Mythglass.

## 4. Docker installieren

Mythglass läuft in einem Container. Docker bringt alles mit, was die Anwendung braucht — du musst
weder Java noch Node auf dem Pi installieren.

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Danach die Sitzung beenden und neu anmelden — über SSH also ausloggen und neu verbinden. Sonst greift
die Gruppenzugehörigkeit nicht und jedes `docker`-Kommando verlangt `sudo`.

Prüfen, ob es sitzt:

```bash
docker run --rm hello-world
```

## 5. Ordner für Bilder und Cache anlegen

Diese beiden Ordner liegen **außerhalb** des Projekts, damit sie ein `git pull` nie anfasst:

```bash
sudo mkdir -p /srv/mythglass/library /srv/mythglass/cache
sudo chown -R "$USER:$USER" /srv/mythglass
```

`library` enthält deine Bilder, `cache` die daraus erzeugten Vorschaubilder. Der Cache darf jederzeit
gelöscht werden — er baut sich beim nächsten Einlesen neu auf.

## 6. Mythglass auf den Pi holen

```bash
git clone https://github.com/ermerp/mythglass.git ~/mythglass
cd ~/mythglass
```

Damit liegt der Quellcode unter `/home/<dein-benutzer>/mythglass`. Das Repository ist öffentlich, du
brauchst also keine Zugangsdaten und keinen SSH-Schlüssel auf dem Pi.

In diesem Ordner arbeitest du ab jetzt. Wichtig sind zwei Dateien:

| Datei | Wofür |
|---|---|
| `compose.yaml` | Beschreibt den Container: Port 8080, die beiden Ordner aus Schritt 5 |
| `Dockerfile` | Beschreibt, wie aus dem Quellcode ein lauffähiges Abbild wird |

## 7. Bauen und starten

```bash
docker compose up -d --build
```

Dieser eine Befehl macht drei Dinge: Er **baut** aus dem Quellcode ein Container-Abbild, **startet**
daraus einen Container, und lässt ihn im Hintergrund weiterlaufen (`-d`). Beim Bauen wird auf dem Pi
Java-Code übersetzt und die Weboberfläche erzeugt; Gradle und Node lädt der Build sich dafür selbst
herunter, du musst nichts davon installieren.

**Nimm dir dafür Zeit.** Auf einem PC dauert der erste Build gut zwei Minuten, auf einem Pi ein
Vielfaches davon. Zwei Dinge können ihn scheitern lassen:

- **Arbeitsspeicher.** Der Gradle- und der Vite-Build zusammen sind hungrig. Auf einem Pi mit 2 GB
  kann der Build ohne Auslagerungsdatei abbrechen — bricht er mit „Killed" oder einem
  OutOfMemoryError ab, ist das die Ursache und nicht der Code.
- **Plattenplatz.** Rechne mit rund 3 GB für Gradle-Cache, Node und die Abbild-Ebenen zusammen.

Jeder weitere Build ist deutlich kürzer, weil der Gradle-Cache erhalten bleibt.

**Läuft es?**

```bash
docker compose ps                       # Status: sollte "running" zeigen
curl -s localhost:8080/api/surfaces     # sollte JSON mit "main" liefern
docker compose logs -f mythglass        # Protokoll, mit Strg-C wieder verlassen
```

Im Protokoll steht unter anderem, wie viele Bilder eingelesen wurden — beim ersten Start
erwartungsgemäß null.

## 8. Bilder auf den Pi bekommen

Die Anwendung hat bewusst keinen Upload: Der Ordner **ist** die Bibliothek. Für den Zugriff vom
Laptop eine Netzwerkfreigabe einrichten:

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

> Für einen ersten schnellen Test geht es auch ohne Samba: `scp bild.jpg pi@mythglass.local:/srv/mythglass/library/`

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

Unterordner dürfen beliebig tief verschachtelt sein; sie erscheinen dann flach als
`Orte/Stadt/Hafen`. Ordner ohne eigene Bilder tauchen nicht auf.

Unterstützt werden JPEG, PNG, GIF und BMP. **WebP und AVIF nicht** — dafür fehlt der
Standard-Java-Bildverarbeitung die Unterstützung, und eine Kachel, die mitten in der Sitzung schwarz
bleibt, wäre schlimmer als eine Datei, die gar nicht erst auftaucht. Solche Dateien werden beim
Einlesen im Protokoll gemeldet.

## 9. Vollbild-Browser einrichten

```bash
cd ~/mythglass
./scripts/setup-kiosk.sh
```

Das legt einen Autostart-Eintrag an und schaltet den Bildschirmschoner ab. Nach dem nächsten Neustart
startet der Pi direkt in die Anzeige.

Ohne Neustart ausprobieren — je nach Pi-OS-Stand heißt das Programm `chromium` oder
`chromium-browser`:

```bash
chromium --kiosk http://localhost:8080/stage/main
```

Beenden mit `Alt`+`F4`.

> Greift der Autostart nach einem Neustart nicht, prüfe zuerst mit diesem Handbefehl, ob die Anzeige
> überhaupt funktioniert. Dann weißt du, ob das Problem bei der Anwendung liegt oder nur beim
> Starten des Browsers.

## 10. Bedienen

Am Handy oder Tablet **`http://mythglass.local:8080`** öffnen. Das ist die Übersicht mit je einem
Feld pro Bildschirm; ein grüner Punkt zeigt, wo gerade ein Gerät hängt.

Fürs Spielen willst du direkt in die Steuerung: **`http://mythglass.local:8080/control`** — die auf
den Startbildschirm legen, das spart am Spieltisch einen Griff.

| Seite | Adresse | Wofür |
|---|---|---|
| Übersicht | `/` | Einrichten, nachsehen was verbunden ist |
| Steuerung | `/control` | Das Lesezeichen des Spielleiters |
| Anzeige | `/stage/main` | Läuft im Kiosk auf dem Pi |

---

## Die erste Probe

Nimm dir dafür zwanzig Minuten, bevor ihr wirklich spielt. Du brauchst ein paar Bilder in der
Bibliothek (Schritt 8). Der Reihe nach:

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
| Nach Codeänderung aktualisieren | `cd ~/mythglass && git pull && docker compose up -d --build` |
| Neu starten | `docker compose restart` |
| Anhalten | `docker compose down` |
| Vorschaubilder verwerfen | `rm -rf /srv/mythglass/cache/thumbs` und neu einlesen |

Alle `docker compose`-Befehle müssen aus `~/mythglass` heraus laufen — dort liegt die `compose.yaml`.

## Wenn etwas nicht funktioniert

**Vom Handy aus gar nichts erreichbar.** Zuerst die IP statt des Namens probieren
(`http://192.168.…:8080`, siehe Schritt 3). Klappt das, löst dein Handy `.local` nicht auf — dann
nimm die IP ins Lesezeichen. Klappt auch das nicht, hängt das Handy in einem anderen Netz.

**Der Build bricht ab.** Siehe Schritt 7 — meistens Arbeitsspeicher oder Plattenplatz.
`df -h` zeigt den freien Platz, `free -h` den Speicher.

**`docker` sagt „permission denied".** Die Gruppenzugehörigkeit aus Schritt 4 greift erst nach einer
neuen Anmeldung.

**Der Monitor bleibt schwarz, die Steuerung meldet «nicht verbunden».** Der Browser auf dem Pi läuft
nicht oder zeigt die falsche Adresse. Prüfen mit `curl -s localhost:8080/api/surfaces` — steht dort
`"connected": false`, hat sich kein Anzeigegerät gemeldet.

**Die Steuerung meldet «Keine Verbindung zum Server».** Der Container läuft nicht, oder das Handy ist
in einem anderen Netz. `docker compose ps` und die WLAN-Einstellungen des Handys prüfen.

**Ein Bild fehlt in der Liste.** Format prüfen (siehe Schritt 8) und ins Protokoll schauen — nicht
unterstützte Dateien werden dort mit ihrer Endung gemeldet.

**Ein ausgetauschtes Bild zeigt noch das alte Motiv.** «Neu einlesen» drücken. Die Anwendung erkennt
den Austausch an Änderungszeit und Größe der Datei.

## Später: der zweite Monitor

Der Tischmonitor mit der Karte braucht keine neue Architektur, sondern zwei Handgriffe:

1. In `backend/src/main/resources/application.yaml` unter `mythglass.stage.surfaces` einen weiteren
   Eintrag anlegen, etwa mit der Kennung `table-map`. Danach neu bauen und starten.
2. `./scripts/setup-kiosk.sh table-map` aufrufen und das Fenster auf den zweiten Bildschirm legen.

Auf der Übersicht erscheint das neue Ausgabeziel von selbst, und die Steuerung zeigt dann eine
Auswahl zwischen den Zielen an.

# Mythglass auf dem Raspberry Pi einrichten

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

# Der kurze Weg

Sechs Schritte. Was dabei im Einzelnen passiert, steht weiter unten unter
[Was das Installationsskript tut](#was-das-installationsskript-tut).

## 1. Auf den Pi kommen

Alle folgenden Befehle laufen in einem Terminal **auf dem Pi** — entweder direkt mit Tastatur am Pi
oder über SSH vom Rechner aus. SSH ist bequemer, weil du die Befehle von hier kopieren kannst. Falls
SSH noch nicht an ist, einmalig am Pi selbst:

```bash
sudo raspi-config nonint do_ssh 0
```

Dann vom Rechner aus verbinden (`pi` und `raspberrypi` durch deinen Benutzer- und Rechnernamen
ersetzen):

```bash
ssh pi@raspberrypi.local
```

## 2. System aktualisieren und Git installieren

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y git
```

## 3. Namen festlegen

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

Nicht jedes Handy und nicht jedes WLAN löst `.local`-Namen auf. Wenn die Anwendung vom Handy aus
nicht erreichbar ist, probiere immer zuerst die IP (`http://192.168.…`). Klappt es damit, liegt es am
Namen und nicht an Mythglass.

## 4. Mythglass holen und einrichten

```bash
git clone https://github.com/ermerp/mythglass.git ~/mythglass
cd ~/mythglass
./scripts/install.sh
```

Das Skript installiert Docker (falls nötig), legt die Ordner an, baut die Anwendung und startet sie.
**Es darf mehrfach laufen** — jeder Schritt prüft zuerst, ob er schon erledigt ist. Wer Docker schon
hat, muss also nichts überspringen, und nach einem Abbruch startest du es einfach erneut.

Der erste Build dauert auf einem Pi ein Vielfaches der zwei Minuten, die er auf einem PC braucht. Am
Ende sagt dir das Skript, wie es weitergeht.

## 5. Vollbild-Browser einrichten

```bash
./scripts/setup-kiosk.sh
```

Legt einen Autostart-Eintrag an und schaltet den Bildschirmschoner ab. Nach dem nächsten Neustart
startet der Pi direkt in die Anzeige. Ohne Neustart ausprobieren — je nach Pi-OS-Stand heißt das
Programm `chromium` oder `chromium-browser`, beenden mit `Alt`+`F4`:

```bash
chromium --kiosk --password-store=basic http://localhost/stage/main
```

> `--password-store=basic` gehört dazu: Ohne diesen Schalter legt Chromium den Schlüssel, mit dem es
> Cookies verschlüsselt, im GNOME-Keyring ab. Der ist nach dem automatischen Login nicht entsperrt,
> also erscheint beim Start ein Passwortdialog — und der Pi bleibt daran hängen, statt die Anzeige zu
> zeigen. Diese Anzeige meldet sich nirgends an und speichert keine Passwörter; der Keyring hat hier
> nichts zu tun. Das Skript setzt den Schalter selbst.

### Wieder aus dem Vollbild heraus

Der Kiosk-Modus hat bewusst keine Leiste und keinen Schließen-Knopf — am Spieltisch soll niemand
versehentlich herausklicken. Drei Wege zurück:

| Weg | Wie |
|---|---|
| Tastatur am Pi | `Alt`+`F4` schließt das Fenster |
| Von einem anderen Rechner | `ssh pi@mythglass.local` und dann `pkill -f chromium` |
| Beim nächsten Start gar nicht erst | `mv ~/.config/autostart/mythglass-main.desktop ~/` |

Den Autostart wieder einschalten: `./scripts/setup-kiosk.sh` erneut aufrufen.

## 6. Bilder ablegen

Nach `/srv/mythglass/library`. Unterordner werden zu Kategorien, Dateinamen zu Anzeigenamen:

```
library/
├── NPCs/
│   ├── Gorak der Wirt.png
│   └── Elenya.jpg
├── Orte/
│   └── Taverne zum Krummen Ast.jpg
└── Titel.jpg
```

Unterordner dürfen beliebig tief verschachtelt sein; sie erscheinen dann flach als
`Orte/Stadt/Hafen`. Ordner ohne eigene Bilder tauchen nicht auf.

Unterstützt werden JPEG, PNG, GIF und BMP. **WebP und AVIF nicht** — dafür fehlt der
Standard-Java-Bildverarbeitung die Unterstützung, und eine Kachel, die mitten in der Sitzung schwarz
bleibt, wäre schlimmer als eine Datei, die gar nicht erst auftaucht. Solche Dateien werden beim
Einlesen im Protokoll gemeldet.

**Bequem vom Laptop aus** geht das über eine Netzwerkfreigabe:

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

Für einen schnellen Test genügt auch:

```bash
scp bild.jpg pi@mythglass.local:/srv/mythglass/library/
```

## Fertig — so wird bedient

Am Handy **`http://mythglass.local`** öffnen. Keine Portangabe nötig.

| Seite | Adresse | Wofür |
|---|---|---|
| Übersicht | `http://mythglass.local` | Einrichten, nachsehen was verbunden ist |
| Steuerung | `http://mythglass.local/control` | Das Lesezeichen des Spielleiters |
| Anzeige | `http://mythglass.local/stage/main` | Läuft im Kiosk auf dem Pi |

Leg dir `/control` auf den Startbildschirm — das spart am Spieltisch einen Griff.

---

# Das Ruhebild

Solange nichts geschaltet ist, zeigt der Monitor nicht Schwarz, sondern ein Ruhebild. Ein schwarzer
Bildschirm sieht aus, als sei etwas kaputt.

**Eigenes Ruhebild:** Leg eine Datei namens `Titel.jpg` (oder `.png`) direkt in
`/srv/mythglass/library` — ohne Unterordner. Groß- und Kleinschreibung spielt keine Rolle. Nach «Neu
einlesen» ist es das Ruhebild, und zwar sofort auch auf einem bereits laufenden Monitor.

**Ohne eigenes Ruhebild** zeigt die Anzeige ein eingebautes Titelbild — bewusst sehr dunkel gehalten,
damit es einen Raum im Halbdunkel nicht erhellt.

Der Name lässt sich in `backend/src/main/resources/application.yaml` unter
`mythglass.stage.idle-image` ändern.

# Aktualisieren

Wenn es eine neue Version gibt:

```bash
cd ~/mythglass
./scripts/update.sh
```

Das holt die Änderungen, baut neu und startet die Anwendung. Es zeigt dir vorher, was sich geändert
hat, und tut nichts, wenn du bereits auf dem neuesten Stand bist. Ändert sich dabei der Start des
Kiosk-Browsers, sagt es dir das.

> Meldet die Zeile `No such file or directory`, stammt dein Klon aus einer Zeit vor diesem Skript —
> es kann sich schlecht selbst ausliefern. Dann einmalig `git pull && docker compose up -d --build`,
> danach ist es da.

**Deine Bilder sind davon nicht betroffen** — sie liegen unter `/srv/mythglass` und damit außerhalb
des Projektordners. Der Kiosk-Browser auf dem Pi verbindet sich nach dem Neustart der Anwendung von
selbst wieder; dort muss niemand etwas anfassen.

Eine Ausnahme: Ändert sich, wie der Kiosk-Browser gestartet wird, muss `./scripts/setup-kiosk.sh`
einmal neu laufen. Das Update sagt nichts darüber — im Zweifel schadet es nie, es erneut aufzurufen.

Der Build dauert deutlich kürzer als beim ersten Mal, weil der Gradle-Cache erhalten bleibt.

---

# Die erste Probe

Nimm dir dafür zwanzig Minuten, bevor ihr wirklich spielt. Der Reihe nach:

1. **Bild schalten.** In der Steuerung eine Kachel antippen — auf dem Monitor erscheint das Bild mit
   einer weichen Überblendung. Die Kachel bekommt einen goldenen Rahmen, oben steht der Name.
2. **Schwarz.** Der Knopf oben rechts nimmt das Bild sofort weg; der Monitor fällt auf das Ruhebild
   zurück. Das ist die Panik-Taste; probier sie aus, damit dein Spielleiter sie im Ernstfall blind
   trifft.
3. **Einpassung.** Unter dem Zahnrad zwischen „Ganz zeigen" und „Fläche füllen" wechseln, während ein
   Bild läuft. Der Monitor ändert sich sofort. Für Portraits ist „Ganz zeigen" richtig, für
   Stimmungsbilder eher „Fläche füllen".
4. **Handy sperren und wieder entsperren.** Danach muss die Steuerung ohne Neuladen weiterarbeiten
   und weiterhin anzeigen, was auf dem Monitor steht.
5. **WLAN kurz aus und wieder an.** Dasselbe Ergebnis erwartet: Die Steuerung meldet kurz „Keine
   Verbindung zum Server" und fängt sich von selbst wieder.
6. **Pi neu starten.** Er soll von allein in die Anzeige booten und das Ruhebild zeigen.
7. **Ein Bild nachlegen.** Über die Freigabe eine Datei in einen Ordner kopieren, dann unter dem
   Zahnrad «Neu einlesen» drücken — sie erscheint in der Liste.

Die Punkte 4 und 5 sind die wichtigsten. Sie prüfen die Eigenschaft, auf der der ganze Entwurf
aufbaut — dass der Server den maßgeblichen Zustand hält und sich jedes Gerät beim Verbinden von
selbst wieder abgleicht.

# Betrieb

| Was | Wie |
|---|---|
| Neue Bilder abgelegt | In der Steuerung unter dem Zahnrad «Neu einlesen» drücken |
| Aktualisieren | `./scripts/update.sh` |
| Protokoll ansehen | `docker compose logs -f mythglass` |
| Neu starten | `docker compose restart` |
| Nach Änderung an `compose.yaml` | `docker compose up -d --build` (siehe unten) |
| Anhalten | `docker compose down` |
| Vorschaubilder verwerfen | `rm -rf /srv/mythglass/cache/thumbs` und neu einlesen |

Alle `docker compose`-Befehle müssen aus `~/mythglass` heraus laufen — dort liegt die `compose.yaml`.

> **`restart` ist nicht dasselbe wie `up -d`.** `docker compose restart` startet den vorhandenen
> Container neu und lässt seine Konfiguration unangetastet — eine geänderte Portzuordnung oder ein
> geänderter Ordner bleiben dabei außen vor. Erst `docker compose up -d` vergleicht mit der
> `compose.yaml` und erzeugt den Container neu, wenn nötig. Wer nach einem `git pull` nur `restart`
> aufruft, läuft mit der alten Konfiguration weiter. `./scripts/update.sh` macht es richtig.

# Wenn etwas nicht funktioniert

**Vom Handy aus gar nichts erreichbar.** Zuerst die IP statt des Namens probieren
(`http://192.168.…`, siehe Schritt 3). Klappt das, löst dein Handy `.local` nicht auf — dann nimm die
IP ins Lesezeichen. Klappt auch das nicht, hängt das Handy in einem anderen Netz. Achte auf getrennte
SSIDs für 2,4 und 5 GHz und auf Gastnetze, die Geräte voneinander abschotten.

**Der Build bricht ab.** Meistens Arbeitsspeicher oder Plattenplatz. `df -h` zeigt den freien Platz,
`free -h` den Speicher. Auf einem Pi mit 2 GB kann der Build ohne Auslagerungsdatei abbrechen —
bricht er mit „Killed" oder einem OutOfMemoryError ab, ist das die Ursache und nicht der Code.

**`docker` sagt „permission denied".** Die Gruppenzugehörigkeit greift erst nach einer neuen
Anmeldung. Einmal ab- und wieder anmelden, dann `./scripts/install.sh` erneut laufen lassen.

**Der Monitor zeigt das Ruhebild, obwohl du etwas geschaltet hast.** Prüfe in der Steuerung, ob dort
„verbunden" steht. Wenn nicht, läuft der Browser auf dem Pi nicht oder zeigt die falsche Adresse.

**Der Kiosk zeigt ERR_CONNECTION_REFUSED.** Der Browser ist da, die Anwendung nicht — jedenfalls
nicht unter der Adresse, die der Browser aufruft. Nachsehen, wo tatsächlich etwas lauscht:

```bash
cd ~/mythglass
docker compose ps
curl -s -o /dev/null -w "Port 80:   %{http_code}\n" http://localhost/api/surfaces
curl -s -o /dev/null -w "Port 8080: %{http_code}\n" http://localhost:8080/api/surfaces
```

Antwortet 8080 statt 80, läuft noch ein Container aus der Zeit vor der Umstellung auf Port 80.
`docker compose up -d --build` holt das nach. Antwortet gar nichts und `docker compose ps` ist leer,
ist der Build abgebrochen — dann sagt `docker compose logs --tail 50 mythglass`, woran.

Wer bei Port 8080 bleiben will oder muss, braucht beides:

```bash
MYTHGLASS_PORT=8080 docker compose up -d --build
MYTHGLASS_URL=http://localhost:8080 ./scripts/setup-kiosk.sh
```

**Der Autostart greift nicht.** Prüfe zuerst mit dem Handbefehl aus Schritt 5, ob die Anzeige
überhaupt funktioniert. Dann weißt du, ob das Problem bei der Anwendung liegt oder nur beim Starten
des Browsers.

**Beim Start des Browsers wird nach einem Passwort gefragt (Keyring).** Der Pi bleibt dann am
Anmeldedialog hängen, statt die Anzeige zu zeigen. Ursache ist der GNOME-Keyring; die Lösung ist der
Schalter `--password-store=basic`, den `setup-kiosk.sh` setzt. Nach einem Update also einmal:

```bash
cd ~/mythglass && ./scripts/setup-kiosk.sh
```

Fragt danach immer noch etwas nach einem Passwort, kommt der Dialog nicht von Chromium, sondern vom
Anmeldevorgang selbst. Dann in `seahorse` („Passwörter und Verschlüsselung") beim Schlüsselbund
_Anmeldung_ das Passwort auf leer setzen — bei einem Gerät, das automatisch hochfährt und sich
nirgends anmeldet, ist das vertretbar.

**Die Steuerung meldet «Keine Verbindung zum Server».** Der Container läuft nicht, oder das Handy ist
in einem anderen Netz. `docker compose ps` und die WLAN-Einstellungen des Handys prüfen.

**Ein Bild fehlt in der Liste.** Format prüfen (siehe Schritt 6) und ins Protokoll schauen — nicht
unterstützte Dateien werden dort mit ihrer Endung gemeldet.

**Ein ausgetauschtes Bild zeigt noch das alte Motiv.** «Neu einlesen» drücken. Die Anwendung erkennt
den Austausch an Änderungszeit und Größe der Datei.

---

# Was das Installationsskript tut

Für alle, die es lieber verstehen oder von Hand machen wollen. `./scripts/install.sh` erledigt genau
diese vier Dinge, jeweils nur dann, wenn sie noch nötig sind:

**Docker installieren.** Mythglass läuft in einem Container; du brauchst weder Java noch Node auf dem
Pi.

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Danach die Sitzung beenden und neu anmelden, sonst verlangt jedes `docker`-Kommando `sudo`.

**Ordner anlegen.** Diese liegen außerhalb des Projekts, damit ein Update sie nie anfasst:

```bash
sudo mkdir -p /srv/mythglass/library /srv/mythglass/cache
sudo chown -R "$USER:$USER" /srv/mythglass
```

**Bauen und starten.**

```bash
docker compose up -d --build
```

Dieser eine Befehl baut aus dem Quellcode ein Container-Abbild, startet daraus einen Container und
lässt ihn im Hintergrund laufen (`-d`). Beim Bauen wird auf dem Pi Java-Code übersetzt und die
Weboberfläche erzeugt; Gradle und Node lädt der Build sich dafür selbst herunter.

**Prüfen, ob es läuft.**

```bash
docker compose ps                   # Status: sollte "running" zeigen
curl -s localhost/api/surfaces      # sollte JSON mit "main" liefern
docker compose logs -f mythglass    # Protokoll, mit Strg-C wieder verlassen
```

## Ein anderer Port oder ein anderer Ordner

Der Container veröffentlicht Port 80, damit am Handy keine Portangabe nötig ist. Beides lässt sich
überschreiben — nützlich, wenn Port 80 belegt ist oder die Bibliothek woanders liegen soll:

```bash
MYTHGLASS_PORT=8080 MYTHGLASS_DATA=/mnt/platte/mythglass ./scripts/install.sh
```

Dasselbe gilt für `./scripts/update.sh`. Wenn du den Port änderst, braucht auch der Kiosk-Browser die
neue Adresse:

```bash
MYTHGLASS_URL=http://localhost:8080 ./scripts/setup-kiosk.sh
```

# Später: der zweite Monitor

Der Tischmonitor mit der Karte braucht keine neue Architektur, sondern zwei Handgriffe:

1. In `backend/src/main/resources/application.yaml` unter `mythglass.stage.surfaces` einen weiteren
   Eintrag anlegen, etwa mit der Kennung `table-map`. Danach `./scripts/update.sh`.
2. `./scripts/setup-kiosk.sh table-map` aufrufen und das Fenster auf den zweiten Bildschirm legen.

Auf der Übersicht erscheint das neue Ausgabeziel von selbst, und die Steuerung zeigt dann eine
Auswahl zwischen den Zielen an.

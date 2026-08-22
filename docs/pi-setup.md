# Mythglass auf dem Raspberry Pi einrichten

Vorausgesetzt: ein Pi 4 oder 5 mit Raspberry Pi OS (64 Bit, Desktop-Variante), am Monitor hinter dem
Spielleiterschirm angeschlossen und im WLAN des Spielorts.

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

> Wenn ihr an wechselnden Orten spielt und euch das fremde WLAN irgendwann auf die Nerven geht: Der
> Pi kann stattdessen ein eigenes WLAN aufspannen. Das ist ein eigener Einrichtungsschritt und für
> den Anfang bewusst nicht Teil dieser Anleitung.

## 2. Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Danach einmal ab- und wieder anmelden, damit die Gruppenzugehörigkeit greift.

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
`smb://mythglass.local/mythglass` (macOS, Linux).

**Struktur:** Unterordner sind die Kategorien, Dateinamen die Anzeigenamen. Also zum Beispiel:

```
library/
├── NPCs/
│   ├── Gorak der Wirt.png
│   └── Elenya.jpg
├── Orte/
│   └── Taverne zum Krummen Ast.jpg
└── Titelbild.jpg
```

Unterstützt werden JPEG, PNG, GIF und BMP. **WebP und AVIF nicht** — dafür fehlt der Standard-Java-
Bildverarbeitung die Unterstützung, und eine Kachel, die mitten in der Sitzung schwarz bleibt, wäre
schlimmer als eine Datei, die gar nicht erst auftaucht. Solche Dateien werden beim Einlesen im
Protokoll gemeldet.

## 5. Anwendung starten

```bash
git clone <dein-repo> ~/mythglass
cd ~/mythglass
docker compose up -d --build
```

Der erste Build lädt Gradle, Node und alle Abhängigkeiten und dauert auf einem Pi eine Weile. Jeder
weitere Build ist dank des Gradle-Cache deutlich kürzer.

Prüfen:

```bash
curl -s localhost:8080/api/surfaces
docker compose logs -f mythglass
```

## 6. Vollbild-Browser einrichten

```bash
./scripts/setup-kiosk.sh
```

Das legt einen Autostart-Eintrag an und schaltet den Bildschirmschoner ab. Nach dem nächsten Neustart
startet der Pi direkt in die Anzeige. Ohne Neustart ausprobieren:

```bash
chromium --kiosk http://localhost:8080/stage/main
```

## 7. Bedienen

Am Handy oder Tablet `http://mythglass.local:8080` öffnen und als Lesezeichen auf den Startbildschirm
legen. Auf einen Blick zu sehen: ob der Monitor verbunden ist, was gerade darauf steht, und der
Schwarz-Knopf.

## Betrieb

| Was | Wie |
|---|---|
| Neue Bilder abgelegt | In der Steuerung «Neu einlesen» drücken |
| Protokoll ansehen | `docker compose logs -f mythglass` |
| Nach Codeänderung aktualisieren | `git pull && docker compose up -d --build` |
| Neu starten | `docker compose restart` |
| Vorschaubilder verwerfen | `rm -rf /srv/mythglass/cache/thumbs` und neu einlesen |

## Wenn etwas nicht funktioniert

**Der Monitor bleibt schwarz, die Steuerung meldet «nicht verbunden».** Der Browser auf dem Pi läuft
nicht oder zeigt die falsche Adresse. Prüfen mit `curl -s localhost:8080/api/surfaces` — steht dort
`"connected": false`, hat sich kein Anzeigegerät gemeldet.

**Die Steuerung meldet «Keine Verbindung zum Server».** Der Container läuft nicht, oder das Handy ist
in einem anderen Netz. `docker compose ps` und die WLAN-Einstellungen des Handys prüfen.

**Ein Bild fehlt in der Liste.** Format prüfen (siehe Schritt 4) und ins Protokoll schauen — nicht
unterstützte Dateien werden dort mit ihrer Endung gemeldet.

**Ein ausgetauschtes Bild zeigt noch das alte Motiv.** «Neu einlesen» drücken. Die Anwendung erkennt
den Austausch an Änderungszeit und Größe der Datei.

## Später: der zweite Monitor

Der Tischmonitor mit der Karte braucht keine neue Architektur, sondern zwei Handgriffe:

1. In `backend/src/main/resources/application.yaml` unter `mythglass.stage.surfaces` einen weiteren
   Eintrag anlegen, etwa mit der Kennung `table-map`.
2. `./scripts/setup-kiosk.sh table-map` aufrufen und das Fenster auf den zweiten Bildschirm legen.

Die Steuerung zeigt dann von selbst eine Auswahl zwischen den Ausgabezielen an.

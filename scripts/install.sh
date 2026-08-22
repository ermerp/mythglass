#!/usr/bin/env bash
#
# Richtet Mythglass auf einem Raspberry Pi ein.
#
# Das Skript ist so gebaut, dass es mehrfach laufen darf: Jeder Schritt prüft zuerst, ob er schon
# erledigt ist. Wer Docker bereits installiert hat, muss also nichts überspringen — das Skript merkt
# es und geht weiter. Nach einem Abbruch kann man es einfach erneut starten.
#
# Aufruf aus dem geklonten Projektverzeichnis heraus:
#
#   ./scripts/install.sh
#
# Was es NICHT tut: den Hostnamen ändern (das braucht einen Neustart) und den Kiosk-Browser
# einrichten (das macht setup-kiosk.sh). Auf beides weist es am Ende hin.

set -euo pipefail

# Ablageort und Port lassen sich überschreiben — auf dem Pi bleibt es bei den Vorgaben, zum
# Ausprobieren auf einem anderen Rechner kann man beides umlenken.
DATA_DIR="${MYTHGLASS_DATA:-/srv/mythglass}"
PORT="${MYTHGLASS_PORT:-80}"

export MYTHGLASS_LIBRARY="${DATA_DIR}/library"
export MYTHGLASS_CACHE="${DATA_DIR}/cache"
export MYTHGLASS_PORT="${PORT}"

BASE_URL="http://localhost"
[[ "${PORT}" == "80" ]] || BASE_URL="http://localhost:${PORT}"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

schritt() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
fertig()  { printf '    \033[32mok\033[0m       %s\n' "$1"; }
getan()   { printf '    \033[33mgemacht\033[0m  %s\n' "$1"; }
hinweis() { printf '    \033[33m!\033[0m        %s\n' "$1"; }

if [[ ! -f "${PROJECT_DIR}/compose.yaml" ]]; then
  echo "compose.yaml nicht gefunden — läuft dieses Skript wirklich aus dem Projektverzeichnis?" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
schritt "Docker"

if command -v docker >/dev/null 2>&1; then
  fertig "Docker ist bereits installiert ($(docker --version | cut -d, -f1))"
else
  getan "Docker wird installiert"
  curl -fsSL https://get.docker.com | sudo sh
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker ist da, aber 'docker compose' fehlt. Bitte das Compose-Plugin nachinstallieren." >&2
  exit 1
fi
fertig "docker compose vorhanden"

if id -nG "$USER" | tr ' ' '\n' | grep -qx docker; then
  fertig "Benutzer $USER ist in der Gruppe docker"
else
  getan "Benutzer $USER wird der Gruppe docker hinzugefügt"
  sudo usermod -aG docker "$USER"
  hinweis "Dafür musst du dich einmal ab- und wieder anmelden."
  hinweis "Bis dahin läuft das Skript weiter mit sudo."
fi

# Solange die Gruppe noch nicht greift, brauchen die Docker-Aufrufe sudo.
if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
else
  DOCKER=(sudo docker)
fi

# ---------------------------------------------------------------------------
schritt "Ordner für Bilder und Cache"

for dir in "${DATA_DIR}/library" "${DATA_DIR}/cache"; do
  if [[ -d "$dir" ]]; then
    fertig "$dir existiert"
  else
    getan "$dir wird angelegt"
    # Ohne sudo versuchen: Unterhalb des eigenen Verzeichnisses ist keins nötig, unterhalb von /srv schon.
    mkdir -p "$dir" 2>/dev/null || sudo mkdir -p "$dir"
  fi
done

if [[ "$(stat -c '%U' "${DATA_DIR}")" == "$USER" ]]; then
  fertig "${DATA_DIR} gehört $USER"
else
  getan "${DATA_DIR} wird auf $USER übertragen"
  sudo chown -R "$USER:$USER" "${DATA_DIR}"
fi

# ---------------------------------------------------------------------------
schritt "Anwendung bauen und starten"

echo "    Das dauert beim ersten Mal deutlich länger als bei jedem weiteren Lauf."
echo "    Der Build lädt Gradle und Node herunter und übersetzt Backend und Oberfläche."
echo

cd "${PROJECT_DIR}"
"${DOCKER[@]}" compose up -d --build

# ---------------------------------------------------------------------------
schritt "Läuft es?"

erreichbar=""
for _ in $(seq 1 60); do
  if curl -fsS -m 2 "${BASE_URL}/api/surfaces" >/dev/null 2>&1; then
    erreichbar="ja"
    break
  fi
  sleep 2
done

if [[ -z "$erreichbar" ]]; then
  echo
  echo "Die Anwendung antwortet nicht auf ${BASE_URL}. Was das Protokoll sagt:" >&2
  "${DOCKER[@]}" compose logs --tail 40 mythglass >&2
  exit 1
fi

fertig "Die Anwendung antwortet auf ${BASE_URL}"
# Ohne "|| true" bricht das Skript hier bei leerer Bibliothek ab: grep findet nichts, gibt 1 zurück,
# und pipefail reicht das weiter — ausgerechnet direkt vor den Hinweisen, was als Nächstes zu tun ist.
bilder="$(curl -fsS "${BASE_URL}/api/library" | grep -o '"id"' | wc -l || true)"
fertig "${bilder} Bild(er) in der Bibliothek"

# ---------------------------------------------------------------------------
schritt "Und jetzt?"

adresse="$(hostname)"
ip="$(hostname -I | awk '{print $1}')"

if [[ "$adresse" != "mythglass" ]]; then
  hinweis "Der Pi heißt derzeit '${adresse}'. Für die Adresse mythglass.local:"
  hinweis "    sudo raspi-config nonint do_hostname mythglass && sudo reboot"
  echo
fi

cat <<ENDE
    1. Bilder ablegen unter ${DATA_DIR}/library
       Unterordner werden zu Kategorien, Dateinamen zu Anzeigenamen.

    2. Vollbild-Browser einrichten:
           ./scripts/setup-kiosk.sh

    3. Am Handy öffnen:
           http://${adresse}.local            (Übersicht)
           http://${adresse}.local/control    (Steuerung — das Lesezeichen)

       Falls dein Handy .local nicht auflöst, nimm die IP: http://${ip}

    Später aktualisieren:  ./scripts/update.sh
ENDE

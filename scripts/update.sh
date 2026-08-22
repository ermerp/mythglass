#!/usr/bin/env bash
#
# Holt die neueste Version von Mythglass und startet sie neu.
#
#   ./scripts/update.sh
#
# Bilder und Vorschaubilder liegen unter /srv/mythglass und damit außerhalb des Projekts — ein Update
# fasst sie nicht an.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_DIR}"

DATA_DIR="${MYTHGLASS_DATA:-/srv/mythglass}"
PORT="${MYTHGLASS_PORT:-80}"

export MYTHGLASS_LIBRARY="${DATA_DIR}/library"
export MYTHGLASS_CACHE="${DATA_DIR}/cache"
export MYTHGLASS_PORT="${PORT}"

BASE_URL="http://localhost"
[[ "${PORT}" == "80" ]] || BASE_URL="http://localhost:${PORT}"

schritt() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
else
  DOCKER=(sudo docker)
fi

schritt "Änderungen holen"

vorher="$(git rev-parse HEAD)"
git pull --ff-only
nachher="$(git rev-parse HEAD)"

if [[ "$vorher" == "$nachher" ]]; then
  echo "    Schon auf dem neuesten Stand — nichts zu tun."
  exit 0
fi

echo
git --no-pager log --oneline "${vorher}..${nachher}"

schritt "Neu bauen und starten"

# Der Gradle-Cache bleibt erhalten, deshalb dauert das deutlich kürzer als die erste Einrichtung.
"${DOCKER[@]}" compose up -d --build

schritt "Läuft es?"

for _ in $(seq 1 60); do
  if curl -fsS -m 2 "${BASE_URL}/api/surfaces" >/dev/null 2>&1; then
    echo "    Die Anwendung antwortet wieder auf ${BASE_URL}"
    echo
    echo "    Der Kiosk-Browser auf dem Pi verbindet sich von selbst neu — dafür muss"
    echo "    niemand etwas anfassen."
    exit 0
  fi
  sleep 2
done

echo
echo "Die Anwendung antwortet nicht. Was das Protokoll sagt:" >&2
"${DOCKER[@]}" compose logs --tail 40 mythglass >&2
exit 1

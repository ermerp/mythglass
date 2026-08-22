#!/usr/bin/env bash
#
# Holt die neueste Version von Mythglass und startet sie neu.
#
#   ./scripts/update.sh
#
# Bilder und Vorschaubilder liegen unter /srv/mythglass und damit außerhalb des Projekts — ein Update
# fasst sie nicht an.
#
# Der gesamte Ablauf steckt in einer Funktion, die erst ganz am Ende aufgerufen wird. Das ist kein
# Stilmittel, sondern notwendig: Das Skript aktualisiert per "git pull" unter anderem sich selbst.
# Bash liest eine Skriptdatei häppchenweise und merkt sich dabei die Leseposition — ändert sich die
# Datei mitten im Lauf, führt Bash ab dieser Position den neuen Inhalt aus und stolpert über
# Bruchstücke. Steht alles in einer Funktion, ist die Datei vollständig eingelesen, bevor der erste
# Befehl läuft.

set -euo pipefail

main() {
  local PROJECT_DIR
  PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  cd "${PROJECT_DIR}"

  local DATA_DIR="${MYTHGLASS_DATA:-/srv/mythglass}"
  local PORT="${MYTHGLASS_PORT:-80}"

  export MYTHGLASS_LIBRARY="${DATA_DIR}/library"
  export MYTHGLASS_CACHE="${DATA_DIR}/cache"
  export MYTHGLASS_PORT="${PORT}"

  local BASE_URL="http://localhost"
  [[ "${PORT}" == "80" ]] || BASE_URL="http://localhost:${PORT}"

  local DOCKER
  if docker info >/dev/null 2>&1; then
    DOCKER=(docker)
  else
    DOCKER=(sudo docker)
  fi

  schritt "Änderungen holen"

  local vorher nachher
  vorher="$(git rev-parse HEAD)"
  git pull --ff-only
  nachher="$(git rev-parse HEAD)"

  if [[ "${vorher}" == "${nachher}" ]]; then
    echo "    Schon auf dem neuesten Stand — nichts zu tun."
    return 0
  fi

  echo
  git --no-pager log --oneline "${vorher}..${nachher}"

  # Ändert sich die Startzeile des Kiosk-Browsers, muss setup-kiosk.sh einmal neu laufen — das kann
  # dieses Skript nicht für den Benutzer entscheiden, aber es kann darauf hinweisen.
  local kiosk_geaendert=""
  if ! git diff --quiet "${vorher}" "${nachher}" -- scripts/setup-kiosk.sh; then
    kiosk_geaendert="ja"
  fi

  schritt "Neu bauen und starten"

  # Der Gradle-Cache bleibt erhalten, deshalb dauert das deutlich kürzer als die erste Einrichtung.
  "${DOCKER[@]}" compose up -d --build

  schritt "Läuft es?"

  local versuch
  for versuch in $(seq 1 60); do
    if curl -fsS -m 2 "${BASE_URL}/api/surfaces" >/dev/null 2>&1; then
      echo "    Die Anwendung antwortet wieder auf ${BASE_URL}"
      echo
      echo "    Der Kiosk-Browser auf dem Pi verbindet sich von selbst neu — dafür muss"
      echo "    niemand etwas anfassen."
      if [[ -n "${kiosk_geaendert}" ]]; then
        echo
        printf '    \033[33m!\033[0m Der Start des Kiosk-Browsers hat sich geändert. Bitte einmal:\n'
        printf '        ./scripts/setup-kiosk.sh\n'
      fi
      return 0
    fi
    sleep 2
  done

  echo
  echo "Die Anwendung antwortet nicht. Was das Protokoll sagt:" >&2
  "${DOCKER[@]}" compose logs --tail 40 mythglass >&2
  return 1
}

schritt() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

# Aufruf und Abbruch in einer Zeile: Bash liest sie als Einheit ein und beendet sich danach, ohne
# noch einmal in die Datei zu schauen. Stünde das "exit" in einer eigenen Zeile, würde Bash nach dem
# Lauf an der alten Leseposition weiterlesen — in einer Datei, die "git pull" inzwischen ersetzt hat.
main "$@"; exit $?

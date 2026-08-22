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
      autostart_auffrischen "${PROJECT_DIR}"
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

# Schreibt vorhandene Autostart-Eintraege neu, damit ein Update wirklich mit einem Befehl auskommt.
# Angelegt wird hier nichts: Wer den Kiosk nie eingerichtet hat, bekommt ihn auch nicht untergeschoben.
autostart_auffrischen() {
  local projekt="$1" eintrag surface url gefunden=0

  shopt -s nullglob
  for eintrag in "${HOME}"/.config/autostart/mythglass-*.desktop; do
    surface="$(basename "${eintrag}" .desktop)"
    surface="${surface#mythglass-}"

    # Eine eigene Adresse aus dem bestehenden Eintrag uebernehmen, statt sie zu ueberschreiben.
    url="$(sed -n 's/^Exec=env MYTHGLASS_URL=\([^ ]*\).*/\1/p' "${eintrag}")"

    if MYTHGLASS_SKIP_BLANKING=1 MYTHGLASS_URL="${url:-${MYTHGLASS_URL:-http://localhost}}" \
        "${projekt}/scripts/setup-kiosk.sh" "${surface}" >/dev/null 2>&1; then
      echo "    Autostart für Surface ${surface} aufgefrischt."
      gefunden=1
    else
      printf '    \033[33m!\033[0m Autostart für Surface %s konnte nicht aufgefrischt werden.\n' "${surface}"
      printf '        Bitte einmal von Hand: ./scripts/setup-kiosk.sh %s\n' "${surface}"
    fi
  done
  shopt -u nullglob

  if (( gefunden == 0 )); then
    return 0
  fi
  echo "    Beim nächsten Neustart des Pi greift der aufgefrischte Eintrag."
}

# Aufruf und Abbruch in einer Zeile: Bash liest sie als Einheit ein und beendet sich danach, ohne
# noch einmal in die Datei zu schauen. Stünde das "exit" in einer eigenen Zeile, würde Bash nach dem
# Lauf an der alten Leseposition weiterlesen — in einer Datei, die "git pull" inzwischen ersetzt hat.
main "$@"; exit $?

#!/usr/bin/env bash
#
# Richtet auf dem Raspberry Pi den Vollbild-Browser ein, der den Spielleiter-Monitor bespielt.
#
# Bewusst außerhalb von Docker: Ein Browser mit Zugriff auf Bildschirm und Grafikeinheit im
# Container einzusperren, kostet viel Konfiguration und bringt hier nichts. Der Pi-Host macht
# "hochfahren, Browser starten", der Container macht die Anwendung.
#
#   ./setup-kiosk.sh [surface-id]
#
# Ohne Angabe wird "main" eingerichtet. Für den späteren Kartenmonitor ein zweites Mal mit der
# entsprechenden Kennung aufrufen.
#
# Dieses Skript legt nur den Autostart-Eintrag an; gestartet wird der Browser von kiosk-start.sh.
# Damit genügt für Änderungen an dessen Aufruf ein Update — hier muss dafür nichts erneut laufen.

set -euo pipefail

SURFACE_ID="${1:-main}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STARTER="${SCRIPT_DIR}/kiosk-start.sh"

# Ohne Port, weil der Container auf Port 80 veröffentlicht. Läuft er woanders, kann die Basisadresse
# über MYTHGLASS_URL gesetzt werden, etwa MYTHGLASS_URL=http://localhost:8080
BASE_URL="${MYTHGLASS_URL:-http://localhost}"

AUTOSTART_DIR="${HOME}/.config/autostart"
DESKTOP_FILE="${AUTOSTART_DIR}/mythglass-${SURFACE_ID}.desktop"

if [[ ! -x "${STARTER}" ]]; then
  echo "kiosk-start.sh fehlt oder ist nicht ausführbar: ${STARTER}" >&2
  exit 1
fi

mkdir -p "${AUTOSTART_DIR}"

cat > "${DESKTOP_FILE}" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Mythglass (${SURFACE_ID})
Comment=Vollbildanzeige fuer die Surface ${SURFACE_ID}
Exec=env MYTHGLASS_URL=${BASE_URL} ${STARTER} ${SURFACE_ID}
X-GNOME-Autostart-enabled=true
DESKTOP

echo "Autostart eingerichtet: ${DESKTOP_FILE}"
echo "  Surface: ${SURFACE_ID}"
echo "  Adresse: ${BASE_URL}/stage/${SURFACE_ID}"
echo "  Starter: ${STARTER}"

# Ein Bildschirm, der mitten in der Sitzung dunkel wird, ist genau das, was dieses Geraet nicht tun soll.
# Beim Auffrischen durch update.sh wird das uebersprungen: Es ist laengst eingestellt, und ein
# sudo-Aufruf mitten in einem Update ist unerwuenscht.
if [[ -n "${MYTHGLASS_SKIP_BLANKING:-}" ]]; then
  echo "Bildschirmschoner unveraendert gelassen."
elif command -v raspi-config >/dev/null 2>&1; then
  echo "Bildschirmschoner wird abgeschaltet ..."
  sudo raspi-config nonint do_blanking 1
else
  echo "Hinweis: raspi-config nicht gefunden — Bildschirmschoner bitte von Hand abschalten."
fi

echo
echo "Fertig. Nach dem naechsten Neustart startet der Pi direkt in die Anzeige."
echo "Der Starter wartet dabei, bis die Anwendung antwortet — so sehen die Spieler"
echo "beim Hochfahren keine Fehlerseite."
echo
echo "Sofort ausprobieren, ohne Neustart:"
echo "  ${STARTER} ${SURFACE_ID}"

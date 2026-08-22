#!/usr/bin/env bash
#
# Startet den Vollbild-Browser für ein Ausgabeziel.
#
#   ./scripts/kiosk-start.sh [surface-id]
#
# Das wird beim Anmelden vom Autostart-Eintrag aufgerufen, den setup-kiosk.sh anlegt. Von Hand
# aufrufen kann man es genauso.
#
# Warum ein eigenes Skript und nicht einfach Chromium im Autostart-Eintrag:
#
#  1. Beim Hochfahren ist der Desktop schneller fertig als Docker und die Anwendung darin. Chromium
#     startet deshalb nicht direkt auf der Anzeige, sondern auf kiosk-splash.html — einem
#     Ladebildschirm, der von selbst weiterspringt, sobald die Anwendung antwortet. So sieht niemand
#     einen leeren Schreibtisch oder eine Fehlerseite.
#  2. Der Autostart-Eintrag zeigt nur noch auf dieses Skript. Ändern sich die Aufrufparameter des
#     Browsers, genügt ein Update — setup-kiosk.sh muss dafür nicht erneut laufen.

set -euo pipefail

SURFACE_ID="${1:-main}"
BASE_URL="${MYTHGLASS_URL:-http://localhost}"
URL="${BASE_URL}/stage/${SURFACE_ID}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPLASH="${SCRIPT_DIR}/kiosk-splash.html"

# Der Name des Chromium-Pakets hat sich zwischen den Raspberry-Pi-OS-Ständen geändert.
if command -v chromium >/dev/null 2>&1; then
  CHROMIUM="$(command -v chromium)"
elif command -v chromium-browser >/dev/null 2>&1; then
  CHROMIUM="$(command -v chromium-browser)"
else
  echo "Chromium ist nicht installiert. Bitte zuerst: sudo apt install -y chromium" >&2
  exit 1
fi

# Aufrufparameter fuer den Kiosk-Browser.
#
# --password-store=basic ist der wichtigste davon: Ohne ihn legt Chromium den Schluessel, mit dem es
# Cookies verschluesselt, im GNOME-Keyring ab. Der ist nach dem automatischen Login nicht entsperrt,
# also erscheint beim Start ein Passwortdialog — und der Pi bleibt daran haengen, statt die Anzeige
# zu zeigen. Diese Anzeige meldet sich nirgends an und speichert keine Passwoerter; der Keyring hat
# hier nichts zu tun.
CHROMIUM_FLAGS=(
  --kiosk
  --password-store=basic
  --noerrdialogs
  --disable-infobars
  --disable-session-crashed-bubble
  --disable-features=Translate
  --check-for-update-interval=31536000
  # Vorsorglich fuer spaeter: Ohne das darf eine Seite ohne Zutun des Benutzers keinen Ton abspielen.
  --autoplay-policy=no-user-gesture-required
)

if [[ -r "${SPLASH}" ]]; then
  # Der Ladebildschirm wartet selbst auf die Anwendung und wechselt dann zur Anzeige.
  START_SEITE="file://${SPLASH}?to=$(printf '%s' "${URL}" | sed 's/&/%26/g')"
  echo "Starte Ladebildschirm, Ziel: ${URL}"
else
  # Ohne Ladebildschirm hier warten, damit der Browser nicht ins Leere laeuft.
  echo "kiosk-splash.html fehlt — warte stattdessen hier auf ${BASE_URL} ..." >&2
  ende=$(( SECONDS + ${MYTHGLASS_KIOSK_TIMEOUT:-120} ))
  while (( SECONDS < ende )); do
    curl -fsS -m 2 "${BASE_URL}/api/surfaces" >/dev/null 2>&1 && break
    sleep 2
  done
  START_SEITE="${URL}"
fi

exec "${CHROMIUM}" "${CHROMIUM_FLAGS[@]}" "${START_SEITE}"

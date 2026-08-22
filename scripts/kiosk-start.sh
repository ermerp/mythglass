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
#     würde ins Leere laufen und den Spielern eine Fehlerseite zeigen. Also wird hier gewartet, bis
#     die Anwendung antwortet.
#  2. Der Autostart-Eintrag zeigt nur noch auf dieses Skript. Ändern sich die Aufrufparameter des
#     Browsers, genügt ein Update — setup-kiosk.sh muss dafür nicht erneut laufen.

set -euo pipefail

SURFACE_ID="${1:-main}"
BASE_URL="${MYTHGLASS_URL:-http://localhost}"
URL="${BASE_URL}/stage/${SURFACE_ID}"

# So lange wird auf die Anwendung gewartet. Läuft die Zeit ab, startet der Browser trotzdem: Eine
# Fehlerseite, die sich selbst wiederholt, ist immer noch besser als ein leerer Bildschirm ohne
# jeden Hinweis.
WARTEZEIT_SEKUNDEN="${MYTHGLASS_KIOSK_TIMEOUT:-120}"

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

echo "Warte auf ${BASE_URL} (bis zu ${WARTEZEIT_SEKUNDEN}s) ..."

ende=$(( SECONDS + WARTEZEIT_SEKUNDEN ))
while (( SECONDS < ende )); do
  if curl -fsS -m 2 "${BASE_URL}/api/surfaces" >/dev/null 2>&1; then
    echo "Anwendung antwortet, starte Anzeige für Surface ${SURFACE_ID}."
    exec "${CHROMIUM}" "${CHROMIUM_FLAGS[@]}" "${URL}"
  fi
  sleep 2
done

echo "Anwendung hat innerhalb von ${WARTEZEIT_SEKUNDEN}s nicht geantwortet — starte trotzdem." >&2
exec "${CHROMIUM}" "${CHROMIUM_FLAGS[@]}" "${URL}"

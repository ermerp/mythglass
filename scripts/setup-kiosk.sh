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

set -euo pipefail

SURFACE_ID="${1:-main}"
# Ohne Port, weil der Container auf Port 80 veröffentlicht. Läuft er woanders, kann die Basisadresse
# über MYTHGLASS_URL gesetzt werden, etwa MYTHGLASS_URL=http://localhost:8080
BASE_URL="${MYTHGLASS_URL:-http://localhost}"
URL="${BASE_URL}/stage/${SURFACE_ID}"
AUTOSTART_DIR="${HOME}/.config/autostart"
DESKTOP_FILE="${AUTOSTART_DIR}/mythglass-${SURFACE_ID}.desktop"

# Der Name des Chromium-Pakets hat sich zwischen den Raspberry-Pi-OS-Ständen geändert.
if command -v chromium >/dev/null 2>&1; then
  CHROMIUM="$(command -v chromium)"
elif command -v chromium-browser >/dev/null 2>&1; then
  CHROMIUM="$(command -v chromium-browser)"
else
  echo "Chromium ist nicht installiert. Bitte zuerst: sudo apt install -y chromium" >&2
  exit 1
fi

mkdir -p "${AUTOSTART_DIR}"

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

cat > "${DESKTOP_FILE}" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Mythglass (${SURFACE_ID})
Comment=Vollbildanzeige fuer die Surface ${SURFACE_ID}
Exec=${CHROMIUM} ${CHROMIUM_FLAGS[*]} ${URL}
X-GNOME-Autostart-enabled=true
DESKTOP

echo "Autostart eingerichtet: ${DESKTOP_FILE}"
echo "  Surface: ${SURFACE_ID}"
echo "  Adresse: ${URL}"

# Ein Bildschirm, der mitten in der Sitzung dunkel wird, ist genau das, was dieses Geraet nicht tun soll.
if command -v raspi-config >/dev/null 2>&1; then
  echo "Bildschirmschoner wird abgeschaltet ..."
  sudo raspi-config nonint do_blanking 1
else
  echo "Hinweis: raspi-config nicht gefunden — Bildschirmschoner bitte von Hand abschalten."
fi

echo
echo "Fertig. Nach dem naechsten Neustart startet der Pi direkt in die Anzeige."
echo "Sofort ausprobieren, ohne Neustart:"
echo "  ${CHROMIUM} ${CHROMIUM_FLAGS[*]} ${URL}"

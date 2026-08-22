import { defineConfig } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

/**
 * Die Tests laufen gegen eine eigene, im globalen Setup erzeugte Bibliothek — nicht gegen `data/`.
 * Sonst würde ein Bild, das jemand zum Ausprobieren hineinlegt, die Tests umwerfen.
 */
export const LIBRARY_DIR = path.join(here, "build", "library");
export const CACHE_DIR = path.join(here, "build", "cache");

/**
 * Bewusst nicht 8080: Auf dem Entwicklungsport läuft oft schon ein bootRun mit der echten
 * Bibliothek aus data/. Liefe der Test dagegen, würde er stillschweigend andere Daten prüfen als
 * erwartet — und grün oder rot aus dem falschen Grund sein.
 */
const PORT = 8099;
const BASE_URL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./tests",
  globalSetup: "./fixtures.ts",
  fullyParallel: false,
  workers: 1,
  reporter: process.env.CI ? "list" : [["list"], ["html", { open: "never" }]],
  timeout: 30_000,

  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  webServer: {
    // Gegen das gebaute Jar, nicht gegen bootRun: So wird genau der Stand geprüft, der auch im
    // Container landet — samt der hineinkopierten Oberfläche.
    command: `java -jar ${path.join(here, "..", "backend", "build", "libs", "mythglass.jar")}`,
    url: `${BASE_URL}/api/surfaces`,
    // Niemals einen fremden Server mitbenutzen: Die Tests bringen ihre eigene Bibliothek mit, ein
    // bereits laufender Server hätte eine andere.
    reuseExistingServer: false,
    timeout: 60_000,
    env: {
      SERVER_PORT: String(PORT),
      MYTHGLASS_LIBRARY_PATH: LIBRARY_DIR,
      MYTHGLASS_CACHE_PATH: CACHE_DIR,
      // Ein Anzeigegerät, das sich nicht abmeldet, fällt erst am fehlgeschlagenen Heartbeat auf.
      // Im Betrieb sind 15 Sekunden dafür in Ordnung, im Test würde es jeden Lauf zäh machen.
      MYTHGLASS_STAGE_HEARTBEAT_INTERVAL: "300ms",
    },
  },
});

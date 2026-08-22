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

export default defineConfig({
  testDir: "./tests",
  globalSetup: "./fixtures.ts",
  fullyParallel: false,
  workers: 1,
  reporter: process.env.CI ? "list" : [["list"], ["html", { open: "never" }]],
  timeout: 30_000,

  use: {
    baseURL: "http://localhost:8080",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  webServer: {
    // Gegen das gebaute Jar, nicht gegen bootRun: So wird genau der Stand geprüft, der auch im
    // Container landet — samt der hineinkopierten Oberfläche.
    command: `java -jar ${path.join(here, "..", "backend", "build", "libs", "mythglass.jar")}`,
    url: "http://localhost:8080/api/surfaces",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: {
      MYTHGLASS_LIBRARY_PATH: LIBRARY_DIR,
      MYTHGLASS_CACHE_PATH: CACHE_DIR,
      // Ein Anzeigegerät, das sich nicht abmeldet, fällt erst am fehlgeschlagenen Heartbeat auf.
      // Im Betrieb sind 15 Sekunden dafür in Ordnung, im Test würde es jeden Lauf zäh machen.
      MYTHGLASS_STAGE_HEARTBEAT_INTERVAL: "300ms",
    },
  },
});

import { test as base, expect, type BrowserContext, type Page } from "@playwright/test";

/**
 * Prüft die Anwendung so, wie sie am Spieltisch benutzt wird: zwei Geräte gleichzeitig.
 *
 * Der Monitor hinter dem Spielleiterschirm und das Handy in der Hand des Spielleiters sind hier
 * wirklich zwei Browser-Kontexte mit eigenen Bildschirmgrößen. Ein Test, der beides in einer Seite
 * zusammenfasst, würde genau das Zusammenspiel nicht prüfen, um das es geht.
 */

const MONITOR = { width: 1280, height: 720 };
const HANDY = { width: 390, height: 844 };

interface Spieltisch {
  /** Öffnet den Monitor hinter dem Spielleiterschirm. */
  monitor(surfaceId?: string): Promise<Page>;
  /** Öffnet die Steuerung im Handy-Format. */
  handy(): Promise<Page>;
}

const test = base.extend<{ tisch: Spieltisch }>({
  tisch: async ({ browser, baseURL, request }, use) => {
    const contexts: BrowserContext[] = [];

    const open = async (options: Parameters<typeof browser.newContext>[0], route: string) => {
      const context = await browser.newContext(options);
      contexts.push(context);
      const page = await context.newPage();
      // Ein Fehler in der Browserkonsole ist ein Fehlschlag — auch wenn die Oberfläche noch etwas zeigt.
      page.on("pageerror", (error) => {
        throw new Error(`${route}: ${error.message}`);
      });
      await page.goto(route);
      return page;
    };

    await use({
      monitor: (surfaceId = "main") => open({ viewport: MONITOR }, `/stage/${surfaceId}`),
      handy: async () => {
        const page = await open({ viewport: HANDY, isMobile: true, hasTouch: true }, "/");
        await expect(page.getByRole("heading", { name: "NPCs" })).toBeVisible();
        return page;
      },
    });

    // Alle Geräte trennen. Ohne das hielte eine Verbindung aus einem früheren Test die Surface
    // weiterhin als "verbunden", und der nächste Test würde den falschen Zustand messen.
    await Promise.all(contexts.map((context) => context.close()));
    // Und den Monitor wieder leeren, damit jeder Test von Schwarz aus startet.
    await request.post(`${baseURL}/api/surfaces/main/blank`);
  },
});

/** Was gerade sichtbar auf der Bühne steht — `null` bedeutet schwarz. */
async function visibleImage(stage: Page): Promise<string | null> {
  return stage.evaluate(() => {
    const front = [...document.querySelectorAll<HTMLElement>(".stage-layer")]
      .find((layer) => getComputedStyle(layer).opacity === "1");
    return front?.querySelector("img")?.getAttribute("src") ?? null;
  });
}

test("Bild schalten, markieren und wieder schwarz schalten", async ({ tisch }) => {
  const stage = await tisch.monitor();
  const control = await tisch.handy();

  await expect(control.getByText("Spielleiter-Monitor verbunden")).toBeVisible();
  await expect(control.locator(".now-showing-text strong")).toHaveText("Nichts");
  expect(await visibleImage(stage)).toBeNull();

  await control.getByRole("button", { name: /Gorak/ }).click();

  await expect(stage.locator(".stage-layer img")).toBeVisible();
  await expect(control.locator(".tile-active .tile-name")).toHaveText("Gorak der Wirt");
  await expect(control.locator(".now-showing-text strong")).toHaveText("Gorak der Wirt");
  await expect.poll(() => visibleImage(stage)).toContain("/full");

  await control.getByRole("button", { name: "Schwarz" }).click();

  await expect(control.locator(".now-showing-text strong")).toHaveText("Nichts");
  await expect.poll(() => visibleImage(stage)).toBeNull();
});

/**
 * Die Kerneigenschaft des Entwurfs. Bricht dieser Test, ist die Anwendung am Spieltisch nicht mehr
 * verlässlich — ein neu geladener Browser oder ein kurz weggebrochenes WLAN würde den Monitor auf
 * einem falschen Stand stehen lassen.
 */
test("Bühne holt sich nach dem Neuladen den Zustand vom Server", async ({ tisch }) => {
  const stage = await tisch.monitor();
  const control = await tisch.handy();

  await control.getByRole("button", { name: /Elenya/ }).click();
  await expect.poll(() => visibleImage(stage)).toContain("/full");
  const before = await visibleImage(stage);

  await stage.reload();

  await expect(stage.locator(".stage-layer img")).toBeVisible();
  await expect.poll(() => visibleImage(stage)).toBe(before);
});

test("Steuerung übernimmt beim Neuladen den laufenden Zustand", async ({ tisch }) => {
  await tisch.monitor();
  const control = await tisch.handy();

  await control.getByRole("button", { name: /Taverne/ }).click();
  await expect(control.locator(".now-showing-text strong")).toHaveText("Taverne zum Krummen Ast");

  await control.reload();

  await expect(control.locator(".now-showing-text strong")).toHaveText("Taverne zum Krummen Ast");
  await expect(control.locator(".tile-active .tile-name")).toHaveText("Taverne zum Krummen Ast");
  await expect(control.getByText("Spielleiter-Monitor verbunden")).toBeVisible();
});

test("Steuerung meldet, wenn kein Monitor zuhört", async ({ tisch }) => {
  const control = await tisch.handy();
  await expect(control.getByText("Spielleiter-Monitor nicht verbunden")).toBeVisible();

  const stage = await tisch.monitor();
  await expect(control.getByText("Spielleiter-Monitor verbunden")).toBeVisible();

  await stage.context().close();
  await expect(control.getByText("Spielleiter-Monitor nicht verbunden")).toBeVisible();
});

test("Bibliothek ist nach Ordnern gruppiert und alphabetisch sortiert", async ({ tisch }) => {
  const control = await tisch.handy();

  // Die Großschreibung kommt aus dem CSS, im Dokument stehen die Ordnernamen wie im Dateisystem.
  await expect(control.locator(".folder h2")).toHaveText(["Allgemein", "NPCs", "Orte"]);
  await expect(control.locator(".folder").nth(1).locator(".tile-name"))
    .toHaveText(["Elenya", "Gorak der Wirt"]);
});

test("Steuerung passt am Handy in die Breite", async ({ tisch }) => {
  const control = await tisch.handy();

  const overflow = await control.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow, "waagerechtes Scrollen am Handy").toBe(0);

  // Der Schwarz-Knopf muss auch nach dem Scrollen erreichbar bleiben.
  await control.mouse.wheel(0, 2000);
  await expect(control.getByRole("button", { name: "Schwarz" })).toBeInViewport();
});

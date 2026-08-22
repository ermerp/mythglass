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
  /** Öffnet die Übersicht, die zu allen Bildschirmen führt. */
  startseite(): Promise<Page>;
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
        const page = await open({ viewport: HANDY, isMobile: true, hasTouch: true }, "/control");
        await expect(page.getByRole("heading", { name: "NPCs" })).toBeVisible();
        return page;
      },
      startseite: () => open({ viewport: HANDY, isMobile: true, hasTouch: true }, "/"),
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

  await control.getByRole("button", { name: "Panik!" }).click();

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
  await expect(control.getByRole("button", { name: "Panik!" })).toBeInViewport();
});

test("Startseite führt zu Steuerung und Ausgabezielen", async ({ tisch }) => {
  const hub = await tisch.startseite();

  await expect(hub.getByRole("link", { name: /Steuerung/ })).toHaveAttribute("href", "/control");

  // Das Feld entsteht aus der Konfiguration des Servers, nicht aus einer Liste im Frontend.
  const monitor = hub.getByRole("link", { name: /Spielleiter-Monitor/ });
  await expect(monitor).toHaveAttribute("href", "/stage/main");
  await expect(monitor).toHaveAttribute("target", "_blank");
});

test("Startseite zeigt, ob an einem Ausgabeziel ein Gerät hängt", async ({ tisch }) => {
  const hub = await tisch.startseite();
  await expect(hub.locator(".hub-card .dot")).toHaveCount(1);
  await expect(hub.locator(".hub-card .dot-on")).toHaveCount(0);

  await tisch.monitor();

  await expect(hub.locator(".hub-card .dot-on")).toHaveCount(1);
});

test("Einstellungen liegen hinter dem Zahnrad", async ({ tisch }) => {
  const control = await tisch.handy();
  const einstellungen = control.getByRole("button", { name: "Einstellungen" });

  // Zugeklappt darf nichts davon Platz kosten.
  await expect(control.getByRole("button", { name: "Ganz zeigen" })).toBeHidden();
  await expect(control.getByRole("button", { name: "Neu einlesen" })).toBeHidden();
  await expect(einstellungen).toHaveAttribute("aria-expanded", "false");

  await einstellungen.click();

  await expect(einstellungen).toHaveAttribute("aria-expanded", "true");
  await expect(control.getByRole("button", { name: "Ganz zeigen" })).toBeVisible();
  await expect(control.getByRole("button", { name: "Neu einlesen" })).toBeVisible();
  await expect(control.getByRole("link", { name: "Alle Bildschirme" })).toHaveAttribute("href", "/");
});

/**
 * Die Einpassung war vorher eine Einstellung für den nächsten Griff — man musste die Kachel erneut
 * suchen, um ihre Wirkung zu sehen. Läuft ein Bild, wirkt sie jetzt sofort darauf.
 */
test("Geänderte Einpassung wirkt sofort auf das laufende Bild", async ({ tisch }) => {
  const stage = await tisch.monitor();
  const control = await tisch.handy();

  await control.getByRole("button", { name: /Gorak/ }).click();
  await expect(stage.locator(".stage-layer img")).toBeVisible();

  await control.getByRole("button", { name: "Einstellungen" }).click();
  await control.getByRole("button", { name: "Fläche füllen" }).click();

  await expect
    .poll(() =>
      stage.evaluate(() => {
        const front = [...document.querySelectorAll<HTMLElement>(".stage-layer")]
          .find((layer) => getComputedStyle(layer).opacity === "1");
        const image = front?.querySelector("img");
        return image ? getComputedStyle(image).objectFit : null;
      }),
    )
    .toBe("cover");
});

/**
 * Ein schwarzer Monitor sieht aus, als sei etwas kaputt. Solange nichts geschaltet ist, zeigt die
 * Anzeige deshalb ein Ruhebild — hier das eingebaute, weil die Testbibliothek kein eigenes enthält.
 */
test("Ohne geschaltetes Bild zeigt die Anzeige das Ruhebild", async ({ tisch }) => {
  const stage = await tisch.monitor();

  await expect(stage.locator(".idle-screen")).toBeVisible();
  await expect(stage.locator(".stage-idle")).toHaveCSS("opacity", "1");
});

test("Ein geschaltetes Bild verdeckt das Ruhebild und gibt es danach wieder frei", async ({ tisch }) => {
  const stage = await tisch.monitor();
  const control = await tisch.handy();

  await control.getByRole("button", { name: /Gorak/ }).click();
  await expect(stage.locator(".stage-layer img")).toBeVisible();
  // Sonst stünde das Ruhebild bei "Ganz zeigen" in den schwarzen Rändern.
  await expect.poll(() => stage.locator(".stage-idle").evaluate((e) => getComputedStyle(e).opacity)).toBe("0");

  await control.getByRole("button", { name: "Panik!" }).click();

  await expect.poll(() => stage.locator(".stage-idle").evaluate((e) => getComputedStyle(e).opacity)).toBe("1");
});

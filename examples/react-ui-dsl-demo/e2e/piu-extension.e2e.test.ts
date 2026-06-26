import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createServer, type ViteDevServer } from "vite";

// The Piu extension demo's novel surface is entirely front-end: a business Piu
// registers a component + toolProvider through `smart-canvas:extend`, and
// `DSLRenderer` resolves both from the runtime store. We mock the two GenUI
// Service endpoints so the flow is deterministic and needs neither the Java
// service nor an LLM. The assertion that the card shows 42/7/15/20 (values that
// only exist in the front-end `queryAlarmSummary`, not in the DSL literals)
// proves the Piu-registered toolProvider actually executed.

const here = dirname(fileURLToPath(import.meta.url));
const demoRoot = resolve(here, "..");
const require = createRequire(import.meta.url);

// `playwright` is a devDependency of @cloudsop/openui-react-ui-dsl (with browsers
// already installed via its postinstall), so resolve it through that package.
const playwrightEntry = require.resolve("playwright", {
  paths: [resolve(demoRoot, "../../packages/react-ui-dsl"), demoRoot],
});

// The deterministic openui-lang the mocked /v1/generate returns. It calls the
// runtime tool and renders the runtime component — both registered via Piu.
const MOCK_DSL = [
  'summary = Query("queryAlarmSummary", {region: "华东一区", window: "1h"}, {total: 0, critical: 0, major: 0, minor: 0})',
  'root = AlarmSummaryCard("华东一区告警概览", summary.total, summary.critical, summary.major, summary.minor)',
].join("\n");

describe("piu extension runtime demo", () => {
  let server: ViteDevServer;
  let browser: import("playwright").Browser;
  let baseUrl: string;

  beforeAll(async () => {
    const { chromium } = (await import(pathToFileURL(playwrightEntry).href)) as typeof import("playwright");

    server = await createServer({
      root: demoRoot,
      configFile: resolve(demoRoot, "vite.config.ts"),
      server: { port: 0 },
      logLevel: "warn",
    });
    await server.listen();

    const address = server.httpServer?.address();
    if (!address || typeof address === "string") {
      throw new Error("Vite dev server did not expose a TCP port");
    }
    baseUrl = `http://localhost:${address.port}`;

    browser = await chromium.launch({ headless: true });
  });

  afterAll(async () => {
    await browser?.close();
    await server?.close();
  });

  it("registers the runtime extension via Piu and renders the tool result through DSLRenderer", async () => {
    const page = await browser.newPage();
    // Only watch for the demo's own failure signatures — ignore unrelated
    // dev-server / asset noise (favicon, source maps, React StrictMode notices).
    const demoErrors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() !== "error") return;
      const text = msg.text();
      if (/alarm-business-piu|runtime extension|DSL engine Piu|window\.Prel/.test(text)) {
        demoErrors.push(text);
      }
    });

    // Mock GenUI Service: generation registration (PUT /v1/generations/{id}).
    await page.route("**/v1/generations/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          extensionId: "piu-alarm-runtime-demo",
          version: "1.0.0",
          componentCount: 1,
          toolCount: 1,
        }),
      });
    });

    // Mock GenUI Service: DSL generation (POST /v1/generate) → fixed openui-lang.
    await page.route("**/v1/generate", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "text/plain; charset=utf-8",
        body: MOCK_DSL,
      });
    });

    await page.goto(`${baseUrl}/?demo=piu-extension`, { waitUntil: "domcontentloaded" });

    // Boot completes: Piu registers the runtime extension, generation registers.
    await page.waitForFunction(
      () => document.querySelector('[data-testid="runtime-status"]')?.textContent === "ready",
      { timeout: 15000 },
    );

    const registration = await page.textContent('[data-testid="registration-summary"]');
    expect(registration).toContain("piu-alarm-runtime-demo");
    expect(registration).toContain("components: 1");
    expect(registration).toContain("tools: 1");

    // Generate → DSLRenderer renders the Piu-registered component.
    await page.click('[data-testid="generate-button"]');

    const card = page.locator('[data-testid="alarm-summary-card"]');
    await card.waitFor({ state: "visible", timeout: 15000 });

    const cardText = (await card.textContent()) ?? "";
    // These values come ONLY from the front-end toolProvider (queryAlarmSummary),
    // not from the DSL literals (all 0) — so seeing them proves the tool ran.
    expect(cardText).toContain("华东一区告警概览");
    expect(cardText).toContain("42"); // total
    expect(cardText).toContain("7"); // critical
    expect(cardText).toContain("15"); // major
    expect(cardText).toContain("20"); // minor

    expect(demoErrors, `demo runtime console errors:\n${demoErrors.join("\n")}`).toHaveLength(0);

    await page.close();
  });
});

import { existsSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import type { Browser, Page } from "playwright";

export interface ScreenshotOptions {
  reportUrl: string;
  screenshotsDir: string;
  fixtureIds: string[];
  waitMs?: number;
}

export interface ScreenshotResult {
  fixtureId: string;
  screenshotPath: string | null;
  error?: string;
}

async function importPlaywright(): Promise<typeof import("playwright")> {
  try {
    return await import("playwright");
  } catch {
    throw new Error(
      "Playwright is not installed. Run: pnpm --filter @openuidev/react-ui-dsl add -D playwright && pnpm exec playwright install chromium",
    );
  }
}

export async function captureFixtureScreenshots(
  options: ScreenshotOptions,
  onProgress?: (done: number, total: number) => void,
): Promise<{ results: ScreenshotResult[]; degraded: boolean }> {
  const { reportUrl, screenshotsDir, fixtureIds, waitMs = 500 } = options;

  mkdirSync(screenshotsDir, { recursive: true });

  const playwright = await importPlaywright();
  const browser: Browser = await playwright.chromium.launch({ headless: true });

  let degraded = false;
  const results: ScreenshotResult[] = [];

  try {
    const page: Page = await browser.newPage();
    await page.setViewportSize({ width: 1280, height: 900 });

    await page.goto(reportUrl, { waitUntil: "networkidle" });
    await page.waitForTimeout(waitMs);

    for (let i = 0; i < fixtureIds.length; i++) {
      const fixtureId = fixtureIds[i]!;
      const screenshotPath = resolve(screenshotsDir, `${fixtureId}.png`);

      try {
        const previewShell = page.locator(`[data-fixture-id="${fixtureId}"] .preview-shell`);
        const count = await previewShell.count();

        if (count === 0) {
          results.push({ fixtureId, screenshotPath: null, error: "Element not found in report page" });
          degraded = true;
        } else {
          await previewShell.screenshot({ path: screenshotPath });
          results.push({ fixtureId, screenshotPath });
        }
      } catch (err) {
        const error = err instanceof Error ? err.message : String(err);
        results.push({ fixtureId, screenshotPath: null, error });
        degraded = true;
      }

      onProgress?.(i + 1, fixtureIds.length);
    }
  } finally {
    await browser.close();
  }

  return { results, degraded };
}

import { existsSync, mkdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { BenchmarkCase } from "../benchmark-loader.ts";
import { runGenerationCli, type GenerationCase } from "./generation-cli.ts";
import { markPhaseDone } from "./run-manifest.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const EVAL_DIR = resolve(__dirname);

export interface RegenOptions {
  runId: string;
  suite: "e2e" | "fuzz" | "benchmark";
  fixtureIds?: string[];
  concurrency?: number;
}

function getStagingDir(runId: string): string {
  return resolve(EVAL_DIR, ".regen-staging", runId);
}

function resolveConcurrency(totalFixtures: number): number {
  const raw = process.env["EVAL_REGEN_CONCURRENCY"];
  const parsed = raw === undefined ? 6 : Number(raw);
  const safe = Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 6;
  return Math.max(1, Math.min(safe, totalFixtures));
}

export function snapshotsDirForSuite(suite: "e2e" | "fuzz" | "benchmark"): string {
  if (suite === "fuzz") return resolve(__dirname, "../fuzz-snapshots");
  if (suite === "benchmark") return resolve(__dirname, "../benchmark-snapshots");
  return resolve(__dirname, "../snapshots");
}

interface RegenResult {
  fixtureId: string;
  success: boolean;
  error?: string;
}

/**
 * Generate DSL for fixtures through the Eval Generation CLI (Java Generation
 * SDK path) with staging + atomic rename. All fixtures must succeed before
 * any file is written to the snapshot directory. Concurrency is enforced
 * inside the CLI via --concurrency.
 */
export async function regenFixtures(
  fixtures: BenchmarkCase[],
  options: RegenOptions,
  onProgress?: (done: number, total: number) => void,
): Promise<{ results: RegenResult[]; success: boolean }> {
  if (!process.env["LLM_API_KEY"]) {
    throw new Error(
      "LLM_API_KEY is not set. The Eval Generation CLI needs it to call the LLM. " +
        "Configure packages/react-ui-dsl/.env and retry.",
    );
  }

  const concurrency = options.concurrency ?? resolveConcurrency(fixtures.length);
  const stagingDir = getStagingDir(options.runId);
  mkdirSync(stagingDir, { recursive: true });

  const cases: GenerationCase[] = fixtures.map((f) => ({
    id: f.id,
    userInput: f.prompt,
    dataModel: f.dataModel as Record<string, unknown>,
  }));

  let completed = 0;
  const cliResults = await runGenerationCli(cases, {
    workDir: stagingDir,
    concurrency,
    onResult: (result) => {
      if (result.status === "ok" && result.dsl) {
        writeFileSync(resolve(stagingDir, `${result.id}.dsl`), result.dsl, "utf-8");
      }
      completed++;
      onProgress?.(completed, fixtures.length);
    },
  });

  const results: RegenResult[] = fixtures.map((fixture) => {
    const cliResult = cliResults.get(fixture.id);
    if (!cliResult) {
      return { fixtureId: fixture.id, success: false, error: "No result from generation CLI" };
    }
    if (cliResult.status !== "ok" || !cliResult.dsl) {
      return { fixtureId: fixture.id, success: false, error: cliResult.error ?? "empty DSL" };
    }
    return { fixtureId: fixture.id, success: true };
  });

  const allSuccess = results.every((r) => r.success);

  if (allSuccess) {
    // Atomic rename: move all staging files to snapshot directory
    const snapshotsDir = snapshotsDirForSuite(options.suite);
    mkdirSync(snapshotsDir, { recursive: true });

    for (const result of results) {
      const stagingPath = resolve(stagingDir, `${result.fixtureId}.dsl`);
      const targetPath = resolve(snapshotsDir, `${result.fixtureId}.dsl`);
      renameSync(stagingPath, targetPath);
    }

    // Clean up staging directory
    rmSync(stagingDir, { recursive: true, force: true });

    // Mark phase as done
    markPhaseDone(options.runId, "regen");

    return { results, success: true };
  } else {
    // Keep staging for debugging, report failures
    const failed = results.filter((r) => !r.success);
    const failedIds = failed.map((r) => r.fixtureId);
    const failedErrors = failed.map((r) => `${r.fixtureId}: ${r.error}`);

    throw new Error(
      `Regen failed for ${failed.length} fixture(s): ${failedIds.join(", ")}\n` +
        `Staging files preserved at: ${stagingDir}\n` +
        `Errors:\n${failedErrors.join("\n")}`,
    );
  }
}

/**
 * Continue regen for fixtures that don't have snapshots yet.
 * Used for recovery after partial regen.
 */
export async function regenMissingFixtures(
  fixtures: BenchmarkCase[],
  options: RegenOptions,
  onProgress?: (done: number, total: number) => void,
): Promise<{ results: RegenResult[]; success: boolean }> {
  const snapshotsDir = snapshotsDirForSuite(options.suite);
  const missing = fixtures.filter((f) => !existsSync(resolve(snapshotsDir, `${f.id}.dsl`)));

  if (missing.length === 0) {
    console.log(`[regen] All ${fixtures.length} fixtures already have snapshots.`);
    return { results: [], success: true };
  }

  console.log(`[regen] ${missing.length} fixtures missing snapshots, generating...`);
  return regenFixtures(missing, options, onProgress);
}

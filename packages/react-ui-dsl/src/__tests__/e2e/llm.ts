import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runGenerationCli } from "./eval/generation-cli.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = resolve(__dirname, "snapshots");
export const DEFAULT_LLM_MODEL = "deepseek-chat";

function isSnapshotRegenEnabled(): boolean {
  return process.env["REGEN_SNAPSHOTS"] === "1";
}

export function getConfiguredLlmModel(): string {
  return process.env["LLM_MODEL"] ?? DEFAULT_LLM_MODEL;
}

/**
 * Generate DSL for a single fixture through the Eval Generation CLI
 * (Java Generation SDK path). There is no TS prompt-assembly fallback —
 * the CLI is the only generation pipeline.
 */
export async function generateDslViaCli(
  id: string,
  prompt: string,
  dataModel: Record<string, unknown>,
): Promise<string> {
  const workDir = mkdtempSync(join(tmpdir(), "genui-eval-fallback-"));
  try {
    const results = await runGenerationCli([{ id, userInput: prompt, dataModel }], {
      workDir,
      concurrency: 1,
    });
    const result = results.get(id);
    if (!result || result.status !== "ok" || !result.dsl) {
      throw new Error(`Generation CLI failed for fixture "${id}": ${result?.error ?? "no result"}`);
    }
    return result.dsl;
  } finally {
    rmSync(workDir, { recursive: true, force: true });
  }
}

export async function loadOrGenerate(
  id: string,
  prompt: string,
  dataModel: Record<string, unknown>,
  snapshotDir: string = SNAPSHOT_DIR,
): Promise<string> {
  const snapshotPath = resolve(snapshotDir, `${id}.dsl`);

  if (!isSnapshotRegenEnabled() && existsSync(snapshotPath)) {
    return readFileSync(snapshotPath, "utf-8") as string;
  }

  // When REGEN_SNAPSHOTS=1 and snapshot doesn't exist, throw error
  // directing user to run the standalone regen command first.
  if (isSnapshotRegenEnabled() && !existsSync(snapshotPath)) {
    throw new Error(
      `Snapshot missing for "${id}" in regen mode. ` +
        `Run: pnpm eval regen (or pnpm eval regen <run-id> to continue) ` +
        `to generate DSL snapshots before running vitest.`,
    );
  }

  const apiKey = process.env["LLM_API_KEY"];
  if (!apiKey) {
    throw new Error(
      `Snapshot missing for "${id}" and LLM_API_KEY is not set. ` +
        `Run: pnpm eval regen (requires LLM_API_KEY in packages/react-ui-dsl/.env)`,
    );
  }

  const dsl = await generateDslViaCli(id, prompt, dataModel);

  mkdirSync(snapshotDir, { recursive: true });
  writeFileSync(snapshotPath, dsl, "utf-8");
  return dsl;
}

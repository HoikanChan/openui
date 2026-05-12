import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";
import { dslLibrary } from "../../genui-lib/dslLibrary";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = resolve(__dirname, "snapshots");
export const DEFAULT_LLM_MODEL = "deepseek-chat";

function isSnapshotRegenEnabled(): boolean {
  return process.env["REGEN_SNAPSHOTS"] === "1";
}

export function getConfiguredLlmModel(): string {
  return process.env["LLM_MODEL"] ?? DEFAULT_LLM_MODEL;
}

export interface GenerateDslOptions {
  prompt: string;
  dataModel: Record<string, unknown>;
  strictness?: "standard" | "strict";
  model?: string;
  apiKey: string;
  baseURL?: string;
  httpsProxy?: string;
}

/**
 * Generate DSL from prompt and data model using LLM.
 * This is a standalone function that does not depend on vitest environment.
 */
export async function generateDsl(options: GenerateDslOptions): Promise<string> {
  const httpsProxy = options.httpsProxy ?? process.env["HTTPS_PROXY"];
  const httpAgent = httpsProxy ? new HttpsProxyAgent(httpsProxy) : undefined;

  const client = new OpenAI({
    apiKey: options.apiKey,
    baseURL: options.baseURL ?? process.env["LLM_BASE_URL"],
    httpAgent,
    dangerouslyAllowBrowser: true,
  });

  const strictness: "standard" | "strict" = options.strictness ?? "standard";
  const systemPrompt = dslLibrary.prompt({ dataModel: { raw: options.dataModel }, strictness });
  const model = options.model ?? getConfiguredLlmModel();

  const response = await client.chat.completions.create({
    model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: options.prompt },
    ],
    temperature: 0,
  });

  const firstChoice = response.choices[0];
  const content = firstChoice?.message.content?.trim();
  if (!content) throw new Error(`LLM returned empty response for fixture: "${options.prompt}"`);
  return content;
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
        `Run: REGEN_SNAPSHOTS=1 LLM_API_KEY=<key> pnpm test:e2e:regen`,
    );
  }

  const strictnessEnv = process.env["REACT_UI_DSL_STRICTNESS"];
  const strictness: "standard" | "strict" =
    strictnessEnv === "strict" ? "strict" : "standard";

  const dsl = await generateDsl({
    prompt,
    dataModel,
    strictness,
    apiKey,
  });

  mkdirSync(snapshotDir, { recursive: true });
  writeFileSync(snapshotPath, dsl, "utf-8");
  return dsl;
}
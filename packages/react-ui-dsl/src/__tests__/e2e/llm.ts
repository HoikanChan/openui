import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import OpenAI from "openai";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = resolve(__dirname, "snapshots");

export async function loadOrGenerate(
  id: string,
  prompt: string,
  dataModel: Record<string, unknown>,
  spec: object,
): Promise<string> {
  const snapshotPath = resolve(SNAPSHOT_DIR, `${id}.dsl`);

  if (!process.env.REGEN_SNAPSHOTS && existsSync(snapshotPath)) {
    return readFileSync(snapshotPath, "utf-8") as string;
  }

  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) {
    throw new Error(
      `Snapshot missing for "${id}" and OPENAI_API_KEY is not set. ` +
        `Run: REGEN_SNAPSHOTS=1 pnpm test:e2e with a valid key.`,
    );
  }

  const dsl = await callLLM(prompt, dataModel, spec, apiKey);
  mkdirSync(SNAPSHOT_DIR, { recursive: true });
  writeFileSync(snapshotPath, dsl, "utf-8");
  return dsl;
}

async function callLLM(
  prompt: string,
  dataModel: Record<string, unknown>,
  spec: object,
  apiKey: string,
): Promise<string> {
  const client = new OpenAI({ apiKey });

  const systemPrompt = `You generate openui-lang DSL.

Available components:
${JSON.stringify(spec, null, 2)}

The user has runtime data accessible via data.xxx with this shape:
${JSON.stringify(dataModel, null, 2)}

Rules:
- Reference data using data.fieldName paths (e.g. data.report.breakdown)
- Return ONLY the DSL. No explanation, no markdown fences.`;

  const response = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: prompt },
    ],
    temperature: 0,
  });

  const content = response.choices[0].message.content?.trim();
  if (!content) throw new Error(`LLM returned empty response for fixture: "${prompt}"`);
  return content;
}

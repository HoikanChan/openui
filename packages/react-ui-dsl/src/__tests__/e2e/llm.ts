import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";
import { dslLibrary } from "../../genui-lib/dslLibrary";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = resolve(__dirname, "snapshots");

export async function loadOrGenerate(
  id: string,
  prompt: string,
  dataModel: Record<string, unknown>,
): Promise<string> {
  const snapshotPath = resolve(SNAPSHOT_DIR, `${id}.dsl`);

  if (!process.env.REGEN_SNAPSHOTS && existsSync(snapshotPath)) {
    return readFileSync(snapshotPath, "utf-8") as string;
  }

  const apiKey = process.env.LLM_API_KEY;
  if (!apiKey) {
    throw new Error(
      `Snapshot missing for "${id}" and LLM_API_KEY is not set. ` +
        `Run: REGEN_SNAPSHOTS=1 LLM_API_KEY=<key> pnpm test:e2e:regen`,
    );
  }

  const dsl = await callLLM(prompt, dataModel, apiKey);
  mkdirSync(SNAPSHOT_DIR, { recursive: true });
  writeFileSync(snapshotPath, dsl, "utf-8");
  return dsl;
}

async function callLLM(
  prompt: string,
  dataModel: Record<string, unknown>,
  apiKey: string,
): Promise<string> {
  const httpAgent = process.env.HTTPS_PROXY
    ? new HttpsProxyAgent(process.env.HTTPS_PROXY)
    : undefined;

  const client = new OpenAI({
    apiKey,
    baseURL: process.env.LLM_BASE_URL,
    httpAgent,
    dangerouslyAllowBrowser: true,
  });

  const systemPrompt = dslLibrary.prompt({ dataModel: { raw: dataModel } });

  const response = await client.chat.completions.create({
    model: process.env.LLM_MODEL ?? "deepseek-chat",
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

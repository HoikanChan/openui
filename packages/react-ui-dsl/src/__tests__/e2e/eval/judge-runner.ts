import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";

export type JudgeRunnerType = "llm-api" | "claude-code" | "codex";

export interface RunnerInput {
  systemPrompt: string;
  userText: string;
  screenshotPath: string | null;
  fixtureId: string;
}

export function resolveRunnerType(): JudgeRunnerType {
  const v = process.env["EVAL_JUDGE_RUNNER"];
  if (v === "claude-code" || v === "codex") return v;
  return "llm-api";
}

function resolveModel(runner: JudgeRunnerType): string {
  const override = process.env["LLM_JUDGE_MODEL"];
  if (override) return override;
  if (runner === "claude-code") return "claude-haiku-4-5-20251001";
  if (runner === "codex") return "gpt-5.4-mini";
  return process.env["LLM_MODEL"] ?? "gpt-4o";
}

export async function invokeRunner(type: JudgeRunnerType, input: RunnerInput): Promise<string> {
  const model = resolveModel(type);
  switch (type) {
    case "claude-code": return runClaudeCode(input, model);
    case "codex": return runCodex(input, model);
    default: return runLlmApi(input, model);
  }
}

// ── llm-api: direct HTTP call to any OpenAI-compatible endpoint ───────────────

async function runLlmApi(input: RunnerInput, model: string): Promise<string> {
  const apiKey = process.env["LLM_API_KEY"];
  if (!apiKey) throw new Error("LLM_API_KEY is required when EVAL_JUDGE_RUNNER=llm-api (default)");

  const httpsProxy = process.env["HTTPS_PROXY"];
  const client = new OpenAI({
    apiKey,
    baseURL: process.env["LLM_BASE_URL"],
    httpAgent: httpsProxy ? new HttpsProxyAgent(httpsProxy) : undefined,
    dangerouslyAllowBrowser: true,
  });

  const content: OpenAI.Chat.ChatCompletionContentPart[] = [
    { type: "text", text: input.userText },
  ];
  if (input.screenshotPath) {
    const base64 = readFileSync(input.screenshotPath).toString("base64");
    content.push({
      type: "image_url",
      image_url: { url: `data:image/png;base64,${base64}`, detail: "high" },
    });
  }

  const response = await client.chat.completions.create({
    model,
    messages: [
      { role: "system", content: input.systemPrompt },
      { role: "user", content },
    ],
    temperature: 0,
  });

  const text = response.choices[0]?.message.content?.trim();
  if (!text) throw new Error(`llm-api judge returned empty response for ${input.fixtureId}`);
  return text;
}

// ── claude-code: spawn `claude --print` with minimal context ──────────────────
//
// --bare               skips hooks, plugins, CLAUDE.md, memory, skill sync
// --disable-slash-commands  prevents any skill invocations in the output
// --tools "Read"       restricts the tool surface to file reading only
// --no-session-persistence  nothing written to disk
// --system-prompt      injects rubric without loading project context

function runClaudeCode(input: RunnerInput, model: string): string {
  const userText = input.screenshotPath
    ? `${input.userText}\n\nThe screenshot of the rendered UI is saved at: ${input.screenshotPath}\nRead it to assess visual rendering quality.`
    : input.userText;

  const args = [
    "--print",
    "--bare",
    "--disable-slash-commands",
    "--no-session-persistence",
    "--system-prompt", input.systemPrompt,
    "--model", model,
    "--tools", "Read",
  ];

  const result = spawnSync("claude", args, {
    input: userText,
    encoding: "utf-8",
    timeout: 120_000,
    maxBuffer: 10 * 1024 * 1024,
  });

  if (result.error) {
    const err = result.error as NodeJS.ErrnoException;
    if (err.code === "ENOENT") {
      throw new Error(
        "claude not found in PATH — install Claude Code to use EVAL_JUDGE_RUNNER=claude-code",
      );
    }
    throw result.error;
  }
  if (result.status === null) {
    throw new Error(`claude timed out for fixture ${input.fixtureId}`);
  }
  if (result.status !== 0) {
    throw new Error(
      `claude exited ${result.status} for ${input.fixtureId}: ${result.stderr?.slice(0, 300)}`,
    );
  }

  return result.stdout;
}

// ── codex: spawn `codex exec` with native image attachment ────────────────────
//
// --ephemeral   no session files written to disk
// -i            native image attachment (no base64 encoding needed in prompt)
// -o            writes only the final assistant message to a temp file
//               → avoids parsing JSONL event stream from stdout

function runCodex(input: RunnerInput, model: string): string {
  // codex exec has no --system-prompt flag; prepend rubric to the user turn
  const combinedPrompt = `${input.systemPrompt}\n\n---\n\n${input.userText}`;

  const tmpDir = mkdtempSync(join(tmpdir(), "eval-judge-"));
  const outputFile = join(tmpDir, "output.txt");

  try {
    const args = ["exec", "--ephemeral", "-m", model, "-o", outputFile];
    if (input.screenshotPath) {
      args.push("-i", input.screenshotPath);
    }

    // codex exec reads from stdin when no prompt argument is provided
    const result = spawnSync("codex", args, {
      input: combinedPrompt,
      encoding: "utf-8",
      timeout: 120_000,
      maxBuffer: 10 * 1024 * 1024,
    });

    if (result.error) {
      const err = result.error as NodeJS.ErrnoException;
      if (err.code === "ENOENT") {
        throw new Error(
          "codex not found in PATH — install Codex to use EVAL_JUDGE_RUNNER=codex",
        );
      }
      throw result.error;
    }
    if (result.status === null) {
      throw new Error(`codex timed out for fixture ${input.fixtureId}`);
    }
    if (result.status !== 0) {
      throw new Error(
        `codex exited ${result.status} for ${input.fixtureId}: ${result.stderr?.slice(0, 300)}`,
      );
    }

    return readFileSync(outputFile, "utf-8");
  } finally {
    rmSync(tmpDir, { recursive: true, force: true });
  }
}

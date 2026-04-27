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

// Wraps a string in single quotes and escapes any embedded single quotes.
// Safe for use as a POSIX shell argument regardless of content.
function sq(s: string): string {
  return `'${s.replace(/'/g, "'\\''")}'`;
}

// Runs cmd via `bash -c` so that shell-script wrappers (e.g. fnm shims) resolve
// correctly. Stdin is piped from `input`; stdout is returned.
function spawnViaBash(
  cmd: string,
  args: string[],
  stdinText: string,
  timeoutMs: number,
  fixtureId: string,
): string {
  const shellCmd = [cmd, ...args.map(sq)].join(" ");
  const result = spawnSync("bash", ["-c", shellCmd], {
    input: stdinText,
    encoding: "utf-8",
    timeout: timeoutMs,
    maxBuffer: 10 * 1024 * 1024,
  });

  if (result.error) {
    const err = result.error as NodeJS.ErrnoException;
    if (err.code === "ENOENT") {
      throw new Error(`bash not found — cannot invoke ${cmd} subprocess`);
    }
    throw result.error;
  }
  if (result.status === null) {
    throw new Error(`${cmd} timed out for fixture ${fixtureId}`);
  }
  if (result.status !== 0) {
    const stderr = result.stderr?.trim() ?? "";
    if (stderr.includes("command not found") || stderr.includes("not found")) {
      throw new Error(
        `${cmd} not found — install it to use EVAL_JUDGE_RUNNER=${cmd === "claude" ? "claude-code" : "codex"}`,
      );
    }
    throw new Error(`${cmd} exited ${result.status} for ${fixtureId}: ${stderr.slice(0, 300)}`);
  }

  return result.stdout;
}

// ── claude-code: spawn `claude --print` with minimal context ──────────────────
//
// --disable-slash-commands  prevents any skill invocations inside the session
// --tools "Read"            restricts the tool surface to file reading only
// --no-session-persistence  nothing written to disk
// --system-prompt           injects rubric (overrides the default system prompt)
//
// Note: --bare is NOT used because it requires ANTHROPIC_API_KEY and bypasses
// OAuth/keychain auth that the developer already has configured.
// Context control is still achieved via --tools, --disable-slash-commands, and
// the explicit --system-prompt (which replaces the default project context).
//
// Invoked via `bash -c` so fnm/npm shell-script shims resolve correctly.

function runClaudeCode(input: RunnerInput, model: string): string {
  const userText = input.screenshotPath
    ? `${input.userText}\n\nThe screenshot of the rendered UI is saved at: ${input.screenshotPath}\nRead it to assess visual rendering quality.`
    : input.userText;

  return spawnViaBash(
    "claude",
    [
      "--print",
      "--disable-slash-commands",
      "--no-session-persistence",
      "--system-prompt", input.systemPrompt,
      "--model", model,
      "--tools", "Read",
    ],
    userText,
    120_000,
    input.fixtureId,
  );
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

    // codex exec reads from stdin when no prompt argument is provided;
    // invoked via bash -c so shell-script shims resolve correctly
    const output = spawnViaBash("codex", args, combinedPrompt, 120_000, input.fixtureId);
    void output; // stdout is discarded; real answer is in outputFile

    return readFileSync(outputFile, "utf-8");
  } finally {
    rmSync(tmpDir, { recursive: true, force: true });
  }
}

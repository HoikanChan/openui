import { spawn } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";

export type JudgeRunnerType = "llm-api" | "claude-code" | "codex" | "gemini";

export interface RunnerInput {
  systemPrompt: string;
  userText: string;
  screenshotPath: string | null;
  fixtureId: string;
}

export function resolveRunnerType(): JudgeRunnerType {
  const v = process.env["EVAL_JUDGE_RUNNER"];
  if (v === "claude-code" || v === "codex" || v === "gemini" || v === "llm-api") return v;
  return "llm-api"; // Default: direct LLM API call (requires LLM_API_KEY + LLM_BASE_URL)
}

export function resolveModel(runner: JudgeRunnerType): string {
  const override = process.env["LLM_JUDGE_MODEL"];
  if (override) return override;
  if (runner === "claude-code") return "claude-haiku-4-5-20251001";
  if (runner === "codex") return "gpt-5.4-mini";
  if (runner === "gemini") return "gemini-2.5-pro";
  return process.env["LLM_MODEL"] ?? "gpt-4o";
}

export async function invokeRunner(type: JudgeRunnerType, input: RunnerInput): Promise<string> {
  const model = resolveModel(type);
  switch (type) {
    case "claude-code": return runClaudeCode(input, model);
    case "codex": return runCodex(input, model);
    case "gemini": return runGemini(input, model);
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

function psq(s: string): string {
  return `'${s.replace(/'/g, "''")}'`;
}

// Runs cmd via `bash -c` so that shell-script wrappers (e.g. fnm shims) resolve
// correctly. Stdin is piped from `input`; stdout is returned. Async so callers
// can run multiple subprocesses concurrently — `spawnSync` would block the
// Node event loop and serialise them regardless of Promise.all wrapping.
function spawnViaBash(
  cmd: string,
  args: string[],
  stdinText: string,
  timeoutMs: number,
  fixtureId: string,
): Promise<string> {
  const shellCmd = [cmd, ...args.map(sq)].join(" ");
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn("bash", ["-c", shellCmd], { stdio: ["pipe", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGKILL");
    }, timeoutMs);

    child.stdout.setEncoding("utf-8");
    child.stderr.setEncoding("utf-8");
    child.stdout.on("data", (chunk: string) => { stdout += chunk; });
    child.stderr.on("data", (chunk: string) => { stderr += chunk; });

    child.on("error", (err) => {
      clearTimeout(timer);
      const e = err as NodeJS.ErrnoException;
      if (e.code === "ENOENT") {
        if (process.platform === "win32") {
          spawnViaPowerShell(cmd, args, stdinText, timeoutMs, fixtureId).then(resolvePromise, rejectPromise);
          return;
        }
        rejectPromise(new Error(`bash not found — cannot invoke ${cmd} subprocess`));
        return;
      }
      rejectPromise(err);
    });

    child.on("close", (code) => {
      clearTimeout(timer);
      if (timedOut) {
        rejectPromise(new Error(`${cmd} timed out for fixture ${fixtureId}`));
        return;
      }
      if (code !== 0) {
        const errOut = stderr.trim();
        if (errOut.includes("command not found") || errOut.includes("not found")) {
          rejectPromise(new Error(
            `${cmd} not found — install it to use EVAL_JUDGE_RUNNER=${cmd === "claude" ? "claude-code" : "codex"}`,
          ));
          return;
        }
        rejectPromise(new Error(`${cmd} exited ${code} for ${fixtureId}: ${errOut.slice(0, 300)}`));
        return;
      }
      resolvePromise(stdout);
    });

    child.stdin.on("error", (err) => {
      clearTimeout(timer);
      rejectPromise(err);
    });
    child.stdin.write(stdinText);
    child.stdin.end();
  });
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

function spawnViaPowerShell(
  cmd: string,
  args: string[],
  stdinText: string,
  timeoutMs: number,
  fixtureId: string,
): Promise<string> {
  const shellCmd = [cmd, ...args.map(psq)].join(" ");
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      shellCmd,
    ], { stdio: ["pipe", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGKILL");
    }, timeoutMs);

    child.stdout.setEncoding("utf-8");
    child.stderr.setEncoding("utf-8");
    child.stdout.on("data", (chunk: string) => { stdout += chunk; });
    child.stderr.on("data", (chunk: string) => { stderr += chunk; });

    child.on("error", (err) => {
      clearTimeout(timer);
      rejectPromise(err);
    });

    child.on("close", (code) => {
      clearTimeout(timer);
      if (timedOut) {
        rejectPromise(new Error(`${cmd} timed out for fixture ${fixtureId}`));
        return;
      }
      if (code !== 0) {
        rejectPromise(new Error(`${cmd} exited ${code} for ${fixtureId}: ${stderr.trim().slice(0, 300)}`));
        return;
      }
      resolvePromise(stdout);
    });

    child.stdin.on("error", (err) => {
      clearTimeout(timer);
      rejectPromise(err);
    });
    child.stdin.write(stdinText);
    child.stdin.end();
  });
}

function spawnViaPowerShellPipeline(
  cmd: string,
  args: string[],
  promptFile: string,
  timeoutMs: number,
  fixtureId: string,
): Promise<string> {
  const shellCmd = `Get-Content -Raw ${psq(promptFile)} | ${[cmd, ...args.map(psq)].join(" ")}`;
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn("powershell.exe", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      shellCmd,
    ], { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGKILL");
    }, timeoutMs);

    child.stdout.setEncoding("utf-8");
    child.stderr.setEncoding("utf-8");
    child.stdout.on("data", (chunk: string) => { stdout += chunk; });
    child.stderr.on("data", (chunk: string) => { stderr += chunk; });

    child.on("error", (err) => {
      clearTimeout(timer);
      rejectPromise(err);
    });

    child.on("close", (code) => {
      clearTimeout(timer);
      if (timedOut) {
        rejectPromise(new Error(`${cmd} timed out for fixture ${fixtureId}`));
        return;
      }
      if (code !== 0) {
        rejectPromise(new Error(`${cmd} exited ${code} for ${fixtureId}: ${stderr.trim().slice(0, 300)}`));
        return;
      }
      resolvePromise(stdout);
    });
  });
}

async function runClaudeCode(input: RunnerInput, model: string): Promise<string> {
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

async function runCodex(input: RunnerInput, model: string): Promise<string> {
  // codex exec has no --system-prompt flag; prepend rubric to the user turn
  const combinedPrompt = `${input.systemPrompt}\n\n---\n\n${input.userText}`;

  const tmpDir = mkdtempSync(join(tmpdir(), "eval-judge-"));
  const outputFile = join(tmpDir, "output.txt");
  const promptFile = join(tmpDir, "prompt.txt");

  try {
    writeFileSync(promptFile, combinedPrompt, "utf-8");
    const args = ["exec", "--ephemeral", "-m", model, "-o", outputFile];
    if (input.screenshotPath) {
      args.push("-i", input.screenshotPath);
    }

    const output = process.platform === "win32"
      ? await spawnViaPowerShellPipeline("codex", args, promptFile, 300_000, input.fixtureId)
      : await spawnViaBash("codex", args, combinedPrompt, 300_000, input.fixtureId);
    void output; // stdout is discarded; real answer is in outputFile

    return readFileSync(outputFile, "utf-8");
  } finally {
    rmSync(tmpDir, { recursive: true, force: true });
  }
}

// ── gemini: spawn `gemini` cli ────────────────────────────────────────────────
async function runGemini(input: RunnerInput, model: string): Promise<string> {
  const tmpDir = mkdtempSync(join(tmpdir(), "eval-judge-gemini-"));
  const sysPromptFile = join(tmpDir, "system.md");
  const promptFile = join(tmpDir, "prompt.txt");

  try {
    writeFileSync(sysPromptFile, input.systemPrompt, "utf-8");
    writeFileSync(promptFile, input.userText, "utf-8");
    
    let args = ["-p"];
    if (input.screenshotPath) {
      args.push(`@${promptFile}`, `@${input.screenshotPath}`);
    } else {
      args.push(`@${promptFile}`);
    }

    const output = await spawnViaBash(
      `GEMINI_SYSTEM_MD=${sq(sysPromptFile)} gemini`,
      args,
      "",
      120_000,
      input.fixtureId,
    );
    return output;
  } finally {
    rmSync(tmpDir, { recursive: true, force: true });
  }
}

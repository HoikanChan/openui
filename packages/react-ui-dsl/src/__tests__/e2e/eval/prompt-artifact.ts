import { createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { printCanonicalPrompt } from "./generation-cli.ts";

const SYSTEM_PROMPT_FILENAME = "system-prompt.txt";

/**
 * Canonical system prompt for a run, assembled by the Java Generation SDK via
 * the Eval Generation CLI (placeholder data model). This is the same assembly
 * path regen uses — the artifact reflects what is actually sent to the model.
 */
export function generateCanonicalPrompt(workDir: string): string {
  return printCanonicalPrompt(workDir);
}

export function computePromptHash(content: string): string {
  return createHash("sha256").update(content, "utf-8").digest("hex");
}

export interface PromptArtifactResult {
  /** Run-relative filename of the written artifact (e.g. "system-prompt.txt"). */
  runRelativePath: string;
  /** SHA-256 hex hash of the full prompt content. */
  hash: string;
}

export function writePromptArtifact(runDir: string): PromptArtifactResult {
  const content = generateCanonicalPrompt(runDir);
  const hash = computePromptHash(content);
  writeFileSync(resolve(runDir, SYSTEM_PROMPT_FILENAME), content, "utf-8");
  return { runRelativePath: SYSTEM_PROMPT_FILENAME, hash };
}

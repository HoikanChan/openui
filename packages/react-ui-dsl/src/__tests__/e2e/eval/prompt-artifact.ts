import { createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { dslLibrary } from "../../../genui-lib/dslLibrary";

const SYSTEM_PROMPT_FILENAME = "system-prompt.txt";

const PLACEHOLDER_DATA_MODEL = {
  __EVAL_DATA_MODEL_PLACEHOLDER__:
    "Fixture data is recorded in report-data.json entries[].dataModel",
};

export function generateCanonicalPrompt(
  strictness: "standard" | "strict" = "standard",
): string {
  return dslLibrary.prompt({
    strictness,
    dataModel: { raw: PLACEHOLDER_DATA_MODEL },
  });
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

export function writePromptArtifact(
  runDir: string,
  strictness: "standard" | "strict" = "standard",
): PromptArtifactResult {
  const content = generateCanonicalPrompt(strictness);
  const hash = computePromptHash(content);
  writeFileSync(resolve(runDir, SYSTEM_PROMPT_FILENAME), content, "utf-8");
  return { runRelativePath: SYSTEM_PROMPT_FILENAME, hash };
}

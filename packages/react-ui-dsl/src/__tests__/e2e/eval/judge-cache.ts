import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { JudgeScore } from "./types.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CACHE_DIR = resolve(__dirname, ".judge-cache");

export interface JudgeCacheKeyInput {
  dsl: string;
  userText: string;
  screenshotPath: string | null;
  rubricText: string;
  judgeModel: string;
}

/**
 * Compute cache key from judge input parameters.
 * Key is sha256 of dsl + userText + screenshot_hash + rubric_hash + model.
 */
export function computeJudgeCacheKey(input: JudgeCacheKeyInput): string {
  const screenshotHash = input.screenshotPath && existsSync(input.screenshotPath)
    ? createHash("sha256").update(readFileSync(input.screenshotPath)).digest("hex").slice(0, 16)
    : "no-screenshot";

  const rubricHash = createHash("sha256").update(input.rubricText).digest("hex").slice(0, 16);
  const userTextHash = createHash("sha256").update(input.userText).digest("hex").slice(0, 16);

  const keyMaterial = [
    input.dsl,
    userTextHash,
    screenshotHash,
    rubricHash,
    input.judgeModel,
  ].join("|");

  return createHash("sha256").update(keyMaterial).digest("hex");
}

function getCachePath(key: string): string {
  const prefix = key.slice(0, 2);
  const dir = resolve(CACHE_DIR, prefix);
  return resolve(dir, `${key}.json`);
}

/**
 * Read cached judge score if exists.
 */
export function readJudgeCache(key: string): JudgeScore | null {
  const path = getCachePath(key);
  if (!existsSync(path)) return null;

  try {
    return JSON.parse(readFileSync(path, "utf-8")) as JudgeScore;
  } catch {
    return null;
  }
}

/**
 * Write judge score to cache.
 * Uses atomic write (.tmp + rename) for concurrency safety.
 */
export function writeJudgeCache(key: string, score: JudgeScore): void {
  const path = getCachePath(key);
  const dir = dirname(path);

  mkdirSync(dir, { recursive: true });

  const tmpPath = resolve(dir, `${key}.tmp`);
  writeFileSync(tmpPath, JSON.stringify(score, null, 2), "utf-8");
  renameSync(tmpPath, path);
}

/**
 * Clear the entire judge cache directory.
 */
export function clearJudgeCache(): void {
  if (existsSync(CACHE_DIR)) {
    rmSync(CACHE_DIR, { recursive: true, force: true });
  }
}
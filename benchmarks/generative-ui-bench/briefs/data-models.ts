import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BAND_FILES = ["b1.json", "b2.json", "b3.json", "b4.json", "b5.json"];

let cached: Record<string, Record<string, unknown>> | null = null;

export function loadDataModels(): Record<string, Record<string, unknown>> {
  if (cached) return cached;
  const merged: Record<string, Record<string, unknown>> = {};
  for (const file of BAND_FILES) {
    const raw = JSON.parse(
      readFileSync(join(__dirname, "data-models", file), "utf8"),
    ) as Record<string, Record<string, unknown>>;
    for (const [key, value] of Object.entries(raw)) {
      if (merged[key]) {
        throw new Error(`Duplicate dataModel key across band files: ${key}`);
      }
      merged[key] = value;
    }
  }
  cached = merged;
  return merged;
}

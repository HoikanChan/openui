import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEFAULT_ENV_PATH = resolve(__dirname, "../../../.env");

function stripMatchingQuotes(value: string): string {
  const firstChar = value[0];
  const lastChar = value[value.length - 1];

  if ((firstChar === '"' || firstChar === "'") && firstChar === lastChar) {
    return value.slice(1, -1);
  }

  return value;
}

export function loadRegenEnvIfNeeded(envPath = DEFAULT_ENV_PATH): void {
  if (!process.env.REGEN_SNAPSHOTS || !existsSync(envPath)) {
    return;
  }

  const fileContent = readFileSync(envPath, "utf-8");

  for (const rawLine of fileContent.split(/\r?\n/)) {
    const line = rawLine.trim();

    if (!line || line.startsWith("#")) {
      continue;
    }

    const separatorIndex = line.indexOf("=");
    if (separatorIndex <= 0) {
      continue;
    }

    const key = line.slice(0, separatorIndex).trim();
    if (!key || process.env[key] !== undefined) {
      continue;
    }

    const rawValue = line.slice(separatorIndex + 1).trim();
    process.env[key] = stripMatchingQuotes(rawValue);
  }
}

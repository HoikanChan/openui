import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { loadRegenEnvIfNeeded } from "./env";

const ORIGINAL_LLM_API_KEY = process.env.LLM_API_KEY;
const ORIGINAL_LLM_BASE_URL = process.env.LLM_BASE_URL;
const ORIGINAL_REGEN_SNAPSHOTS = process.env.REGEN_SNAPSHOTS;

function restoreEnv() {
  if (ORIGINAL_LLM_API_KEY === undefined) {
    delete process.env.LLM_API_KEY;
  } else {
    process.env.LLM_API_KEY = ORIGINAL_LLM_API_KEY;
  }

  if (ORIGINAL_LLM_BASE_URL === undefined) {
    delete process.env.LLM_BASE_URL;
  } else {
    process.env.LLM_BASE_URL = ORIGINAL_LLM_BASE_URL;
  }

  if (ORIGINAL_REGEN_SNAPSHOTS === undefined) {
    delete process.env.REGEN_SNAPSHOTS;
  } else {
    process.env.REGEN_SNAPSHOTS = ORIGINAL_REGEN_SNAPSHOTS;
  }
}

describe("loadRegenEnvIfNeeded", () => {
  afterEach(() => {
    restoreEnv();
  });

  it("does nothing when REGEN_SNAPSHOTS is unset", () => {
    delete process.env.REGEN_SNAPSHOTS;
    delete process.env.LLM_API_KEY;

    const tempDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-env-test-"));
    const envPath = join(tempDir, ".env");
    writeFileSync(envPath, "LLM_API_KEY=sk-from-dotenv\n", "utf-8");

    try {
      loadRegenEnvIfNeeded(envPath);
      expect(process.env.LLM_API_KEY).toBeUndefined();
    } finally {
      rmSync(tempDir, { recursive: true, force: true });
    }
  });

  it("loads missing vars from .env when REGEN_SNAPSHOTS is set", () => {
    process.env.REGEN_SNAPSHOTS = "1";
    delete process.env.LLM_API_KEY;
    delete process.env.LLM_BASE_URL;

    const tempDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-env-test-"));
    const envPath = join(tempDir, ".env");
    writeFileSync(envPath, 'LLM_API_KEY="sk-from-dotenv"\nLLM_BASE_URL=https://example.test/v1\n', "utf-8");

    try {
      loadRegenEnvIfNeeded(envPath);
      expect(process.env.LLM_API_KEY).toBe("sk-from-dotenv");
      expect(process.env.LLM_BASE_URL).toBe("https://example.test/v1");
    } finally {
      rmSync(tempDir, { recursive: true, force: true });
    }
  });

  it("does not override vars that are already in the environment", () => {
    process.env.REGEN_SNAPSHOTS = "1";
    process.env.LLM_API_KEY = "sk-from-shell";

    const tempDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-env-test-"));
    const envPath = join(tempDir, ".env");
    writeFileSync(envPath, "LLM_API_KEY=sk-from-dotenv\n", "utf-8");

    try {
      loadRegenEnvIfNeeded(envPath);
      expect(process.env.LLM_API_KEY).toBe("sk-from-shell");
    } finally {
      rmSync(tempDir, { recursive: true, force: true });
    }
  });
});

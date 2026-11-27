import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The canonical prompt is assembled by the Java Generation SDK via the CLI's
// print-prompt command. Mock that boundary; the TS helper only orchestrates
// hashing + persistence (real prompt content is covered by the CLI/SDK tests).
const { printCanonicalPrompt } = vi.hoisted(() => ({ printCanonicalPrompt: vi.fn() }));

vi.mock("./generation-cli.ts", () => ({ printCanonicalPrompt }));

import { computePromptHash, generateCanonicalPrompt, writePromptArtifact } from "./prompt-artifact";

const SAMPLE_PROMPT =
  "You are a UI generator.\nComponents: Stack, Table, Col, Text\n" +
  "Data model: __EVAL_DATA_MODEL_PLACEHOLDER__\n";

describe("prompt artifact helper", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    printCanonicalPrompt.mockReturnValue(SAMPLE_PROMPT);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("delegates to the CLI print-prompt path with the given work dir", () => {
    const prompt = generateCanonicalPrompt("/some/run/dir");
    expect(prompt).toBe(SAMPLE_PROMPT);
    expect(printCanonicalPrompt).toHaveBeenCalledWith("/some/run/dir");
  });

  it("computed hash is a 64-char hex SHA-256 string", () => {
    const hash = computePromptHash(generateCanonicalPrompt("/dir"));
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it("prompt content changes alter the computed hash", () => {
    const hashA = computePromptHash("prompt A");
    const hashB = computePromptHash("prompt B");
    expect(hashA).not.toEqual(hashB);
  });

  it("writePromptArtifact writes system-prompt.txt and returns run-relative path and hash", () => {
    const tmpDir = mkdtempSync(resolve(tmpdir(), "prompt-artifact-test-"));
    try {
      const result = writePromptArtifact(tmpDir);
      expect(result.runRelativePath).toBe("system-prompt.txt");
      expect(result.hash).toMatch(/^[0-9a-f]{64}$/);

      const written = readFileSync(resolve(tmpDir, "system-prompt.txt"), "utf-8");
      expect(written).toBe(SAMPLE_PROMPT);
      expect(written).toContain("__EVAL_DATA_MODEL_PLACEHOLDER__");
      expect(computePromptHash(written)).toBe(result.hash);
    } finally {
      rmSync(tmpDir, { recursive: true });
    }
  });
});

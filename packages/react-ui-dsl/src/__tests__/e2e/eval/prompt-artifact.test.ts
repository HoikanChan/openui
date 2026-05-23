import { describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { tmpdir } from "node:os";
import {
  generateCanonicalPrompt,
  computePromptHash,
  writePromptArtifact,
} from "./prompt-artifact";

describe("prompt artifact helper", () => {
  it("generates a non-empty system prompt for standard strictness", () => {
    const prompt = generateCanonicalPrompt("standard");
    expect(prompt.length).toBeGreaterThan(100);
  });

  it("generates a different prompt for strict vs standard strictness", () => {
    const standard = generateCanonicalPrompt("standard");
    const strict = generateCanonicalPrompt("strict");
    expect(standard).not.toEqual(strict);
  });

  it("strictness change alters the computed hash", () => {
    const hashStandard = computePromptHash(generateCanonicalPrompt("standard"));
    const hashStrict = computePromptHash(generateCanonicalPrompt("strict"));
    expect(hashStandard).not.toEqual(hashStrict);
  });

  it("computed hash is a 64-char hex SHA-256 string", () => {
    const hash = computePromptHash(generateCanonicalPrompt("standard"));
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it("canonical prompt contains component signatures, builtins, and standard rules", () => {
    const prompt = generateCanonicalPrompt("standard");
    expect(prompt).toContain("Stack");
    expect(prompt).toContain("Table");
    expect(prompt).toContain("Col");
    expect(prompt).toContain("Text");
  });

  it("canonical prompt data model section uses the placeholder, not fixture data", () => {
    const prompt = generateCanonicalPrompt("standard");
    expect(prompt).toContain("__EVAL_DATA_MODEL_PLACEHOLDER__");
    expect(prompt).not.toContain("fixtureId");
  });

  it("writePromptArtifact writes system-prompt.txt and returns run-relative path and hash", () => {
    const tmpDir = mkdtempSync(resolve(tmpdir(), "prompt-artifact-test-"));
    try {
      const result = writePromptArtifact(tmpDir, "standard");
      expect(result.runRelativePath).toBe("system-prompt.txt");
      expect(result.hash).toMatch(/^[0-9a-f]{64}$/);

      const written = readFileSync(resolve(tmpDir, "system-prompt.txt"), "utf-8");
      expect(written).toContain("__EVAL_DATA_MODEL_PLACEHOLDER__");
      expect(computePromptHash(written)).toBe(result.hash);
    } finally {
      rmSync(tmpDir, { recursive: true });
    }
  });

  it("writePromptArtifact with strict strictness produces a different hash than standard", () => {
    const tmpDir = mkdtempSync(resolve(tmpdir(), "prompt-artifact-test-"));
    try {
      const standard = writePromptArtifact(tmpDir, "standard");
      const strict = writePromptArtifact(tmpDir, "strict");
      expect(standard.hash).not.toEqual(strict.hash);
    } finally {
      rmSync(tmpDir, { recursive: true });
    }
  });

  it("strict canonical prompt contains STRICT-specific rules absent from standard", () => {
    const standard = generateCanonicalPrompt("standard");
    const strict = generateCanonicalPrompt("strict");
    // Strict rules are tagged with "STRICT:" in dslLibrary — the artifact must reflect strictness
    expect(strict).toContain("STRICT:");
    expect(standard).not.toContain("STRICT:");
  });

  it("strict artifact hash matches hash computed from written file content", () => {
    const tmpDir = mkdtempSync(resolve(tmpdir(), "prompt-artifact-test-"));
    try {
      const result = writePromptArtifact(tmpDir, "strict");
      const written = readFileSync(resolve(tmpDir, "system-prompt.txt"), "utf-8");
      expect(computePromptHash(written)).toBe(result.hash);
      expect(written).toContain("STRICT:");
    } finally {
      rmSync(tmpDir, { recursive: true });
    }
  });
});

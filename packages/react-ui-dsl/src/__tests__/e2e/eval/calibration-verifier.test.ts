import { describe, expect, it } from "vitest";
import {
  forwardPromptCorrections,
  getPendingJudgeCorrections,
  getPendingPromptCorrections,
} from "./calibration-verifier.ts";
import type { CorrectionEntry } from "./types.ts";

function makeCorrection(overrides: Partial<CorrectionEntry>): CorrectionEntry {
  return {
    id: `c_${Math.random().toString(36).slice(2)}`,
    target: "judge",
    state: "pending",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe("getPendingJudgeCorrections", () => {
  it("returns only pending judge-targeted corrections", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "judge", state: "pending" }),
      makeCorrection({ target: "judge", state: "applied" }),
      makeCorrection({ target: "prompt", state: "pending" }),
    ];
    const result = getPendingJudgeCorrections(corrections);
    expect(result).toHaveLength(1);
    expect(result[0]!.target).toBe("judge");
    expect(result[0]!.state).toBe("pending");
  });

  it("returns empty when no pending judge corrections", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "judge", state: "applied" }),
      makeCorrection({ target: "prompt", state: "pending" }),
    ];
    expect(getPendingJudgeCorrections(corrections)).toHaveLength(0);
  });
});

describe("getPendingPromptCorrections", () => {
  it("returns only pending prompt-targeted corrections", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "prompt", state: "pending" }),
      makeCorrection({ target: "prompt", state: "forwarded_to_optimizer" }),
      makeCorrection({ target: "judge", state: "pending" }),
    ];
    const result = getPendingPromptCorrections(corrections);
    expect(result).toHaveLength(1);
    expect(result[0]!.target).toBe("prompt");
  });
});

describe("forwardPromptCorrections", () => {
  it("marks pending prompt corrections as forwarded_to_optimizer", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "prompt", state: "pending", text_feedback: "Use table for lists" }),
      makeCorrection({ target: "judge", state: "pending" }),
    ];
    const { forwarded, toInclude } = forwardPromptCorrections(corrections);
    expect(forwarded).toHaveLength(1);
    expect(forwarded[0]!.state).toBe("forwarded_to_optimizer");
    expect(toInclude).toHaveLength(1);
    expect(toInclude[0]!.text_feedback).toBe("Use table for lists");
  });

  it("excludes corrections without text_feedback from toInclude", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "prompt", state: "pending" }),
    ];
    const { forwarded, toInclude } = forwardPromptCorrections(corrections);
    expect(forwarded).toHaveLength(1);
    expect(toInclude).toHaveLength(0);
  });

  it("returns empty arrays when no pending prompt corrections", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({ target: "prompt", state: "forwarded_to_optimizer" }),
      makeCorrection({ target: "judge", state: "pending" }),
    ];
    const { forwarded, toInclude } = forwardPromptCorrections(corrections);
    expect(forwarded).toHaveLength(0);
    expect(toInclude).toHaveLength(0);
  });

  it("preserves fixtureId in toInclude when present", () => {
    const corrections: CorrectionEntry[] = [
      makeCorrection({
        target: "prompt",
        state: "pending",
        fixtureId: "table-basic",
        text_feedback: "Add pagination",
      }),
    ];
    const { toInclude } = forwardPromptCorrections(corrections);
    expect(toInclude[0]!.fixtureId).toBe("table-basic");
  });
});

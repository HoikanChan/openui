import { describe, expect, it } from "vitest";
import { resolveSystemPrompt } from "./systemPrompt";

describe("resolveSystemPrompt", () => {
  it("returns an explicit system prompt override when provided", () => {
    expect(resolveSystemPrompt("custom prompt", { region: "APAC" })).toBe("custom prompt");
  });

  it("falls back to generated prompt when no override is provided", () => {
    const result = resolveSystemPrompt(undefined, { region: "APAC" });

    expect(result).toContain("region");
  });
});

import { describe, expect, it } from "vitest";
import { CardSchema } from "./schema";

describe("react-ui-dsl Card schema", () => {
  it("accepts variant and width props", () => {
    expect(
      CardSchema.safeParse({
        children: [],
        variant: "sunk",
        width: "full",
      }).success,
    ).toBe(true);

    expect(
      CardSchema.safeParse({
        variant: "clear",
        width: "standard",
      }).success,
    ).toBe(true);
  });

  it("defaults are valid (all props optional)", () => {
    expect(CardSchema.safeParse({}).success).toBe(true);
  });

  it("rejects flex layout props", () => {
    expect(CardSchema.safeParse({ direction: "row" }).success).toBe(false);
    expect(CardSchema.safeParse({ gap: "l" }).success).toBe(false);
    expect(CardSchema.safeParse({ align: "stretch" }).success).toBe(false);
    expect(CardSchema.safeParse({ justify: "between" }).success).toBe(false);
    expect(CardSchema.safeParse({ wrap: true }).success).toBe(false);
  });

  it("rejects unknown props like header", () => {
    expect(
      CardSchema.safeParse({
        children: [],
        header: { title: "Runtime rollout" },
      }).success,
    ).toBe(false);
  });
});

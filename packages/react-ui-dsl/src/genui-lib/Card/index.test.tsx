import { describe, expect, it } from "vitest";
import { CardSchema } from "./schema";

describe("react-ui-dsl Card schema", () => {
  it("accepts the contract flex props", () => {
    expect(
      CardSchema.safeParse({
        children: [],
        direction: "row",
        align: "center",
        justify: "between",
        wrap: true,
      }).success,
    ).toBe(true);
  });

  it("defaults are valid (all props optional)", () => {
    expect(CardSchema.safeParse({}).success).toBe(true);
  });

  it("rejects renderer-only props removed by the contract", () => {
    expect(CardSchema.safeParse({ variant: "sunk" }).success).toBe(false);
    expect(CardSchema.safeParse({ width: "full" }).success).toBe(false);
    expect(CardSchema.safeParse({ gap: "l" }).success).toBe(false);
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

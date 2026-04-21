import { describe, expect, it } from "vitest";
import { CardSchema } from "./schema";

describe("react-ui-dsl Card schema", () => {
  it("accepts the react-ui-aligned flex layout props", () => {
    const result = CardSchema.safeParse({
      align: "stretch",
      children: [],
      direction: "row",
      gap: "l",
      justify: "between",
      variant: "sunk",
      wrap: true,
    });

    expect(result.success).toBe(true);
  });

  it("rejects the legacy header and width props", () => {
    expect(
      CardSchema.safeParse({
        children: [],
        header: { title: "Runtime rollout" },
      }).success,
    ).toBe(false);

    expect(
      CardSchema.safeParse({
        children: [],
        width: "full",
      }).success,
    ).toBe(false);
  });
});

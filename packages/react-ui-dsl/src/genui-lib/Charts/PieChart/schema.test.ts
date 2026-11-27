import { describe, expect, it } from "vitest";
import { dslLibrary } from "../../dslLibrary";
import { PieChartSchema } from "./schema";

describe("PieChart schema", () => {
  it("accepts numeric and percentage-string values", () => {
    expect(PieChartSchema.safeParse({ labels: ["A", "B"], values: [80, 20] }).success).toBe(true);
    expect(
      PieChartSchema.safeParse({ labels: ["A", "B"], values: ["80%", "20%"] }).success,
    ).toBe(true);
  });

  it("accepts isPercentage", () => {
    const result = PieChartSchema.safeParse({
      labels: ["A"],
      values: [1],
      isPercentage: true,
    });
    expect(result.success).toBe(true);
  });

  it("declares isPercentage and string-capable values in the contract spec", () => {
    const props = dslLibrary.toSpec().components.PieChart.propsSchema?.properties ?? {};
    expect(props.isPercentage).toEqual({ type: "boolean" });
    expect(props.values).toEqual({
      type: "array",
      items: { anyOf: [{ type: "number" }, { type: "string" }] },
    });
  });
});

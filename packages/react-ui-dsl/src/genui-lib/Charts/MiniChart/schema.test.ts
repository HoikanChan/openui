import { describe, expect, it } from "vitest";
import { MiniChartSchema } from "./schema";

describe("MiniChart schema", () => {
  it("accepts number arrays and object arrays as compact single-series data", () => {
    expect(MiniChartSchema.safeParse({ type: "line", data: [12, 18, 15] }).success).toBe(true);
    expect(
      MiniChartSchema.safeParse({
        type: "bar",
        data: [
          { value: 3, label: "Mon" },
          { value: 5, label: "Tue" },
        ],
      }).success,
    ).toBe(true);
  });

  it("rejects full-chart props that do not belong to the MiniChart contract", () => {
    const result = MiniChartSchema.safeParse({
      type: "area",
      data: [4, 6, 5],
      labels: ["Mon", "Tue", "Wed"],
      series: [{ category: "Load", values: [4, 6, 5] }],
    });

    expect(result.success).toBe(false);
  });
});

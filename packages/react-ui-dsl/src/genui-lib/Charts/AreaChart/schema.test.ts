import { describe, expect, it } from "vitest";
import { dslLibrary } from "../../dslLibrary";
import { AreaChartSchema } from "./schema";

describe("AreaChart schema", () => {
  it("accepts thresholdType and thresholdValue", () => {
    const result = AreaChartSchema.safeParse({
      labels: ["00:00", "01:00"],
      series: [{ category: "Mbps", values: [1, 2] }],
      thresholdType: "high",
      thresholdValue: 80,
    });
    expect(result.success).toBe(true);
  });

  it("rejects an unknown thresholdType", () => {
    expect(
      AreaChartSchema.safeParse({
        labels: ["a"],
        series: [{ category: "s", values: [1] }],
        thresholdType: "middle",
      }).success,
    ).toBe(false);
  });

  it("declares threshold props in the contract spec", () => {
    const props = dslLibrary.toSpec().components.AreaChart.propsSchema?.properties ?? {};
    expect(props.thresholdType).toEqual({ type: "string", enum: ["low", "high"] });
    expect(props.thresholdValue).toEqual({ type: "number" });
  });
});

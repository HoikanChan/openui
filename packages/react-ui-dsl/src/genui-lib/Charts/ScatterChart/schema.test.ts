import { describe, expect, it } from "vitest";
import { ScatterChartSchema } from "./schema";
import { ScatterSeriesSchema } from "../ScatterSeries";

describe("ScatterChart schema", () => {
  it("requires datasets on ScatterChart", () => {
    const result = ScatterChartSchema.safeParse({});

    expect(result.success).toBe(false);
  });

  it("requires points on ScatterSeries", () => {
    const result = ScatterSeriesSchema.safeParse({ name: "Devices" });

    expect(result.success).toBe(false);
  });
});

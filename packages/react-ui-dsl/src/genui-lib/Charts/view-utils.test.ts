import { describe, expect, it } from "vitest";
import { buildScatterSeries } from "./view-utils";

describe("chart view utils", () => {
  it("maps scatter z values to bubble sizes", () => {
    const [series] = buildScatterSeries([
      {
        name: "Devices",
        points: [
          { x: 10, y: 0.5, z: 20 },
          { x: 20, y: 1.2, z: 40 },
        ],
      },
    ]);

    expect(series).toMatchObject({
      type: "scatter",
      name: "Devices",
      data: [
        [10, 0.5, 20],
        [20, 1.2, 40],
      ],
    });
    expect(series).toHaveProperty("symbolSize");
    expect((series as { symbolSize: (value: number[]) => number }).symbolSize([10, 0.5, 20])).toBeGreaterThan(0);
    expect((series as { symbolSize: (value: number[]) => number }).symbolSize([20, 1.2, 40])).toBeGreaterThan(
      (series as { symbolSize: (value: number[]) => number }).symbolSize([10, 0.5, 20]),
    );
  });

  it("unwraps ScatterSeries element nodes passed through DSL props", () => {
    const [series] = buildScatterSeries([
      {
        type: "element",
        typeName: "ScatterSeries",
        partial: false,
        props: {
          name: "Core Routers",
          points: [
            { x: 5, y: 0.1 },
            { x: 8, y: 0.2 },
            { x: 12, y: 0.3 },
          ],
        },
      } as any,
    ]);

    expect(series).toMatchObject({
      type: "scatter",
      name: "Core Routers",
      data: [
        [5, 0.1],
        [8, 0.2],
        [12, 0.3],
      ],
    });
  });
});

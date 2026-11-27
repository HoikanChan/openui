import { describe, expect, it } from "vitest";
import {
  buildChartOption,
  buildScatterSeries,
  getAutoMiniChartHeight,
  getAutoMiniChartWidth,
  getRecentMiniChartDataThatFits,
  normalizeMiniChartData,
  normalizeSeriesItems,
} from "./view-utils";

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

  it("unwraps Series element nodes passed through DSL props", () => {
    expect(
      normalizeSeriesItems([
        {
          type: "element",
          typeName: "Series",
          partial: false,
          props: {
            category: "Revenue",
            values: [420000, 530000, 610000],
          },
        } as any,
      ]),
    ).toEqual([
      {
        category: "Revenue",
        values: [420000, 530000, 610000],
      },
    ]);
  });

  it("normalizes mini chart number arrays into labeled points", () => {
    expect(normalizeMiniChartData([12, 18, 15])).toEqual([
      { value: 12, label: "Item 1" },
      { value: 18, label: "Item 2" },
      { value: 15, label: "Item 3" },
    ]);
  });

  it("preserves explicit mini chart labels when normalizing objects", () => {
    expect(
      normalizeMiniChartData([
        { value: 3, label: "Mon" },
        { value: 5 },
      ]),
    ).toEqual([
      { value: 3, label: "Mon" },
      { value: 5, label: "Item 2" },
    ]);
  });

  it("truncates mini chart data to the most recent points that fit the width", () => {
    expect(getRecentMiniChartDataThatFits([1, 2, 3, 4, 5], 60, 20)).toEqual([3, 4, 5]);
    expect(
      getRecentMiniChartDataThatFits(
        [
          { value: 1, label: "A" },
          { value: 2, label: "B" },
          { value: 3, label: "C" },
          { value: 4, label: "D" },
        ],
        39,
        20,
      ),
    ).toEqual([{ value: 4, label: "D" }]);
  });

  it("derives compact mini chart heights from container width with sensible bounds", () => {
    expect(getAutoMiniChartHeight(0)).toBe(36);
    expect(getAutoMiniChartHeight(100)).toBe(24);
    expect(getAutoMiniChartHeight(160)).toBe(35);
    expect(getAutoMiniChartHeight(260)).toBe(44);
  });

  it("caps mini chart width by intrinsic density instead of always filling wide containers", () => {
    expect(getAutoMiniChartWidth(4, 20, 0)).toBe(96);
    expect(getAutoMiniChartWidth(4, 20, 400)).toBe(96);
    expect(getAutoMiniChartWidth(12, 20, 400)).toBe(240);
    expect(getAutoMiniChartWidth(12, 20, 160)).toBe(160);
  });

  it("normalizes chart legends to bottom center", () => {
    expect(
      buildChartOption({
        legend: {
          orient: "vertical",
          left: "left",
        },
      }),
    ).toMatchObject({
      legend: {
        orient: "horizontal",
        left: "center",
        bottom: 0,
      },
    });
  });
});

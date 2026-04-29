// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as echarts from "echarts";
import { MiniChartView } from "./index";

vi.mock("echarts", () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
  registerTheme: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.mocked(echarts.init).mockClear();
});

function getLastChartOption() {
  const chartInstance = vi.mocked(echarts.init).mock.results[0]?.value as { setOption?: ReturnType<typeof vi.fn> } | undefined;
  expect(chartInstance?.setOption).toBeDefined();
  return chartInstance?.setOption?.mock.calls.at(-1)?.[0] as {
    series?: Array<Record<string, unknown>>;
    xAxis?: Record<string, unknown>;
    yAxis?: Record<string, unknown>;
  };
}

describe("MiniChartView", () => {
  it("dispatches line charts to a compact sparkline option with full-width sizing", () => {
    const { container } = render(<MiniChartView type="line" data={[12, 18, 15, 21]} />);

    const option = getLastChartOption();
    expect(option.series?.[0]).toMatchObject({
      type: "line",
      data: [12, 18, 15, 21],
      smooth: true,
    });
    expect(option.xAxis).toMatchObject({ show: false, type: "category" });
    expect(option.yAxis).toMatchObject({ show: false, type: "value" });
    expect(container.querySelector('div[style*="width: 100%"]')).not.toBeNull();
    expect(container.querySelector('div[style*="width: 96px"][style*="height: 24px"]')).not.toBeNull();
  });

  it("dispatches bar and area charts with type-specific defaults", () => {
    render(
      <>
        <MiniChartView type="bar" data={[3, 5, 4]} />
        <MiniChartView type="area" data={[7, 9, 8]} />
      </>,
    );

    const initCalls = vi.mocked(echarts.init).mock.results;
    const barChart = initCalls[0]?.value as { setOption?: ReturnType<typeof vi.fn> };
    const areaChart = initCalls[1]?.value as { setOption?: ReturnType<typeof vi.fn> };
    const barOption = barChart.setOption?.mock.calls.at(-1)?.[0] as { series?: Array<Record<string, unknown>> };
    const areaOption = areaChart.setOption?.mock.calls.at(-1)?.[0] as { series?: Array<Record<string, unknown>> };

    expect(barOption.series?.[0]).toMatchObject({ type: "bar", data: [3, 5, 4] });
    expect(areaOption.series?.[0]).toMatchObject({ type: "line", data: [7, 9, 8], areaStyle: expect.any(Object) });
  });

  it("uses explicit height while keeping width responsive", () => {
    const { container } = render(<MiniChartView type="line" data={[12, 18, 15, 21]} height={28} />);

    expect(container.querySelector('div[style*="width: 96px"][style*="height: 28px"]')).not.toBeNull();
  });
});

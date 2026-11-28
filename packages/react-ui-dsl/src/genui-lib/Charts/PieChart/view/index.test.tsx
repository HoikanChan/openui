import { describe, expect, it } from "vitest";
import { PieChartView } from ".";

describe("PieChartView responsive labels", () => {
  it("hides outside labels below 420px so ECharts cannot truncate them to ellipses", () => {
    const rendered = PieChartView({
      labels: ["Smart TV", "Mobile"],
      values: [54, 24],
      variant: "donut",
      isPercentage: true,
    });

    expect(rendered.props.option.media).toEqual([
      {
        query: { maxWidth: 420 },
        option: { series: [{ label: { show: false }, labelLine: { show: false } }] },
      },
    ]);
  });

  it("keeps the legend and tooltip carrying the names and values", () => {
    const rendered = PieChartView({
      labels: ["Smart TV", "Mobile"],
      values: [54, 24],
      isPercentage: true,
    });
    const option = rendered.props.option;

    expect(option.series[0].data).toEqual([
      { name: "Smart TV", value: 54 },
      { name: "Mobile", value: 24 },
    ]);
    expect(option.tooltip).toEqual({ trigger: "item", formatter: "{b}: {d}%" });
  });
});

"use client";
import type * as echarts from "echarts";
import { BaseChart } from "../../../../components/chart/BaseChart";
import { buildChartOption } from "../../view-utils";

type PieChartViewProps = {
  labels: string[];
  values: (number | string)[];
  variant?: "pie" | "donut";
  isPercentage?: boolean;
};

// Below this width outside labels are truncated to ellipses. Hide them and let
// the legend and tooltip carry the names instead.
const PIE_LABEL_MAX_WIDTH = 420;
const NARROW_PIE_LABEL_OPTION = {
  query: { maxWidth: PIE_LABEL_MAX_WIDTH },
  option: { series: [{ label: { show: false }, labelLine: { show: false } }] },
};

function toPieValue(value: number | string): number {
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function PieChartView({ labels, values, variant, isPercentage }: PieChartViewProps) {
  const radius = variant === "donut" ? ["40%", "70%"] : "70%";
  const option: echarts.EChartsOption = {
    series: [
      {
        type: "pie",
        radius,
        data: (labels ?? []).map((name, i) => ({
          name,
          value: toPieValue((values ?? [])[i] ?? 0),
        })),
      },
    ],
    legend: { orient: "vertical", left: "left" },
    media: [NARROW_PIE_LABEL_OPTION],
    tooltip: isPercentage ? { trigger: "item", formatter: "{b}: {d}%" } : { trigger: "item" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

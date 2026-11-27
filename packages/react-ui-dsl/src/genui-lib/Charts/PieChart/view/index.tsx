"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption } from "../../view-utils";

type PieChartViewProps = {
  labels: string[];
  values: (number | string)[];
  variant?: "pie" | "donut";
  isPercentage?: boolean;
};

function toPieValue(value: number | string): number {
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function PieChartView({ labels, values, variant, isPercentage }: PieChartViewProps) {
  const radius = variant === "donut" ? ["40%", "70%"] : "70%";
  const option: echarts.EChartsOption = {
    series: [{
      type: "pie",
      radius,
      data: (labels ?? []).map((name, i) => ({
        name,
        value: toPieValue((values ?? [])[i] ?? 0),
      })),
    }],
    legend: { orient: "vertical", left: "left" },
    tooltip: isPercentage
      ? { trigger: "item", formatter: "{b}: {d}%" }
      : { trigger: "item" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

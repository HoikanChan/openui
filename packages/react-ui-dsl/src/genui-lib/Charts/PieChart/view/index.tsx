"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption } from "../../view-utils";

type PieChartViewProps = {
  labels: string[];
  values: number[];
  variant?: "pie" | "donut";
};

export function PieChartView({ labels, values, variant }: PieChartViewProps) {
  const radius = variant === "donut" ? ["40%", "70%"] : "70%";
  const option: echarts.EChartsOption = {
    series: [{
      type: "pie",
      radius,
      data: (labels ?? []).map((name, i) => ({ name, value: (values ?? [])[i] })),
    }],
    legend: { orient: "vertical", left: "left" },
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

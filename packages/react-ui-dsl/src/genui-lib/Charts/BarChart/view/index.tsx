"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type SeriesItem = { category: string; values: number[] };

type BarChartViewProps = {
  labels: string[];
  series: SeriesItem[];
  variant?: "grouped" | "stacked";
  xLabel?: string;
  yLabel?: string;
};

export function BarChartView({ labels, series, variant, xLabel, yLabel }: BarChartViewProps) {
  const isStacked = variant === "stacked";
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: labels, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: series.map(s => ({
      type: "bar",
      name: s.category,
      data: s.values,
      ...(isStacked ? { stack: "total" } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={option} />;
}

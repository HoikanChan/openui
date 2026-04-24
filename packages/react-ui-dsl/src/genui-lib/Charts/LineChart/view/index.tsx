"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { normalizeSeriesItems } from "../../view-utils";

type SeriesItem = { category: string; values: number[] };

type LineChartViewProps = {
  labels: string[];
  series: SeriesItem[];
  variant?: "linear" | "smooth" | "step";
  xLabel?: string;
  yLabel?: string;
};

export function LineChartView({ labels, series, variant, xLabel, yLabel }: LineChartViewProps) {
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: labels, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: safeSeries.map(s => ({
      type: "line",
      name: s.category,
      data: s.values,
      smooth: variant === "smooth",
      ...(variant === "step" ? { step: "middle" as const } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={option} />;
}

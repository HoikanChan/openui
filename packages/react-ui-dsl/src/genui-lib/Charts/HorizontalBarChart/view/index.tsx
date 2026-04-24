"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { normalizeSeriesItems } from "../../view-utils";

type SeriesItem = { category: string; values: number[] };

type HorizontalBarChartViewProps = {
  labels: string[];
  series: SeriesItem[];
  variant?: "grouped" | "stacked";
  xLabel?: string;
  yLabel?: string;
};

export function HorizontalBarChartView({ labels, series, variant, xLabel, yLabel }: HorizontalBarChartViewProps) {
  const isStacked = variant === "stacked";
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const option: echarts.EChartsOption = {
    xAxis: { type: "value", ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "category", data: labels, ...(yLabel ? { name: yLabel } : {}) },
    series: safeSeries.map(s => ({
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

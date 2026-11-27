"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, normalizeSeriesItems } from "../../view-utils";

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
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: labels, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: safeSeries.map(s => ({
      type: "bar",
      name: s.category,
      data: s.values,
      ...(isStacked ? { stack: "total" } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

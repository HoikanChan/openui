"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, normalizeSeriesItems } from "../../view-utils";

type SeriesItem = { category: string; values: number[] };

type AreaChartViewProps = {
  labels: string[];
  series: SeriesItem[];
  variant?: "linear" | "smooth" | "step";
  xLabel?: string;
  yLabel?: string;
};

export function AreaChartView({ labels, series, variant, xLabel, yLabel }: AreaChartViewProps) {
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: labels, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: safeSeries.map(s => ({
      type: "line",
      name: s.category,
      data: s.values,
      areaStyle: {},
      smooth: variant === "smooth",
      ...(variant === "step" ? { step: "middle" as const } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

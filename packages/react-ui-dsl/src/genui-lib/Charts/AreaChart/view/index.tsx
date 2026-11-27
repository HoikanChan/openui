"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, normalizeSeriesItems } from "../../view-utils";

type SeriesItem = { category: string; values: number[] };

type AreaChartViewProps = {
  labels: string[];
  series: SeriesItem[];
  stacked?: boolean;
  variant?: "linear" | "smooth" | "step";
  xLabel?: string;
  yLabel?: string;
};

export function AreaChartView({ labels, series, stacked, variant, xLabel, yLabel }: AreaChartViewProps) {
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: labels, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: safeSeries.map((s, i) => ({
      type: "line",
      name: s.category,
      data: s.values,
      areaStyle: stacked && i === 0 ? { opacity: 0 } : {},
      lineStyle: stacked && i === 0 ? { opacity: 0 } : undefined,
      stack: stacked ? "band" : undefined,
      smooth: variant === "smooth",
      ...(variant === "step" ? { step: "middle" as const } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

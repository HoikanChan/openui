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
  thresholdType?: "low" | "high";
  thresholdValue?: number;
};

type ThresholdMarkLine = {
  silent: boolean;
  symbol: "none";
  label: { formatter: string };
  lineStyle: { type: "dashed"; color: string };
  data: { yAxis: number }[];
};

function buildThresholdMarkLine(
  thresholdType: "low" | "high" | undefined,
  thresholdValue: number | undefined,
): ThresholdMarkLine | undefined {
  if (thresholdValue === undefined) return undefined;
  return {
    silent: true,
    symbol: "none",
    label: { formatter: String(thresholdValue) },
    lineStyle: {
      type: "dashed",
      color: thresholdType === "low" ? "#faad14" : "#ff4d4f",
    },
    data: [{ yAxis: thresholdValue }],
  };
}

export function AreaChartView({
  labels,
  series,
  stacked,
  variant,
  xLabel,
  yLabel,
  thresholdType,
  thresholdValue,
}: AreaChartViewProps) {
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const thresholdMarkLine = buildThresholdMarkLine(thresholdType, thresholdValue);
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
      ...(i === 0 && thresholdMarkLine ? { markLine: thresholdMarkLine } : {}),
    })),
    legend: {},
    tooltip: { trigger: "axis" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

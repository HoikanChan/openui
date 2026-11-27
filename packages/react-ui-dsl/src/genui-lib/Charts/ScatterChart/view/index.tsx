"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, buildScatterSeries } from "../../view-utils";

const EPOCH_MS_MIN = 1e11;
const EPOCH_MS_MAX = 2e13;

function looksLikeEpochMs(datasets: { points: { x: number }[] }[]): boolean {
  const xValues = datasets.flatMap((d) => d.points.map((p) => p.x));
  return xValues.length > 0 && xValues.every((x) => x > EPOCH_MS_MIN && x < EPOCH_MS_MAX);
}

type ScatterChartViewProps = {
  datasets: { name: string; points: { x: number; y: number; z?: number }[] }[];
  xLabel?: string;
  xType?: "time" | "value";
  yLabel?: string;
};

export function ScatterChartView({ datasets, xLabel, xType, yLabel }: ScatterChartViewProps) {
  const resolvedXType = xType ?? (looksLikeEpochMs(datasets ?? []) ? "time" : "value");
  const option: echarts.EChartsOption = {
    xAxis: { type: resolvedXType, ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: buildScatterSeries(datasets ?? []),
    legend: {},
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

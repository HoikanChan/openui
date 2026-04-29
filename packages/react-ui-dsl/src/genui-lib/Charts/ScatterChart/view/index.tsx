"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, buildScatterSeries } from "../../view-utils";

type ScatterChartViewProps = {
  datasets: { name: string; points: { x: number; y: number; z?: number }[] }[];
  xLabel?: string;
  yLabel?: string;
};

export function ScatterChartView({ datasets, xLabel, yLabel }: ScatterChartViewProps) {
  const option: echarts.EChartsOption = {
    xAxis: { type: "value", ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: buildScatterSeries(datasets ?? []),
    legend: {},
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={buildChartOption(option)} />;
}

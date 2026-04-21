"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type PointItem = { x: number; y: number; z?: number };
type DatasetItem = { name: string; points: PointItem[] };

type ScatterChartViewProps = {
  datasets: DatasetItem[];
  xLabel?: string;
  yLabel?: string;
};

export function ScatterChartView({ datasets, xLabel, yLabel }: ScatterChartViewProps) {
  const option: echarts.EChartsOption = {
    xAxis: { type: "value", ...(xLabel ? { name: xLabel } : {}) },
    yAxis: { type: "value", ...(yLabel ? { name: yLabel } : {}) },
    series: datasets.map(ds => ({
      type: "scatter",
      name: ds.name,
      data: ds.points.map(p => p.z !== undefined ? [p.x, p.y, p.z] : [p.x, p.y]),
    })),
    legend: {},
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={option} />;
}

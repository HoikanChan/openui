"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type SeriesItem = { category: string; values: number[] };

type RadarChartViewProps = {
  labels: string[];
  series: SeriesItem[];
};

export function RadarChartView({ labels, series }: RadarChartViewProps) {
  const maxValues = labels.map((_, i) =>
    Math.max(...series.map(s => s.values[i] ?? 0))
  );
  const option: echarts.EChartsOption = {
    radar: {
      indicator: labels.map((name, i) => ({ name, max: maxValues[i] })),
    },
    series: [{
      type: "radar",
      data: series.map(s => ({ name: s.category, value: s.values })),
    }],
    legend: {},
    tooltip: {},
  };
  return <BaseChart option={option} />;
}

"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type HeatmapChartViewProps = {
  xLabels: string[];
  yLabels: string[];
  values: number[][];
};

export function HeatmapChartView({ xLabels, yLabels, values }: HeatmapChartViewProps) {
  const data: [number, number, number][] = [];
  let minVal = Infinity;
  let maxVal = -Infinity;
  const safeYLabels = yLabels ?? [];
  const safeXLabels = xLabels ?? [];
  const safeValues = values ?? [];
  for (let y = 0; y < safeYLabels.length; y++) {
    for (let x = 0; x < safeXLabels.length; x++) {
      const v = safeValues[y]?.[x] ?? 0;
      data.push([x, y, v]);
      if (v < minVal) minVal = v;
      if (v > maxVal) maxVal = v;
    }
  }
  const option: echarts.EChartsOption = {
    xAxis: { type: "category", data: safeXLabels, splitArea: { show: true } },
    yAxis: { type: "category", data: safeYLabels, splitArea: { show: true } },
    visualMap: {
      type: "continuous",
      calculable: true,
      min: minVal,
      max: maxVal,
      orient: "horizontal",
      left: "center",
      bottom: "0%",
    },
    series: [{
      type: "heatmap",
      data,
      label: { show: false },
    }],
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={option} />;
}

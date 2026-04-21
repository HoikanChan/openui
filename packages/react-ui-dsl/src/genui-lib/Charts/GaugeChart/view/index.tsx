"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type GaugeChartViewProps = {
  readings: { name: string; value: number }[];
  min?: number;
  max?: number;
};

export function GaugeChartView({ readings, min = 0, max = 100 }: GaugeChartViewProps) {
  const option: echarts.EChartsOption = {
    series: [{
      type: "gauge",
      min,
      max,
      data: (readings ?? []).map(r => ({ name: r.name, value: r.value })),
    }],
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={option} />;
}

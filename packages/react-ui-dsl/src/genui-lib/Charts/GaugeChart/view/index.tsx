"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type GaugeChartViewProps = {
  readings: { name: string; value: number }[];
  min?: number;
  max?: number;
};

export function GaugeChartView({ readings, min = 0, max = 100 }: GaugeChartViewProps) {
  const isUnitInterval = max <= 1.0 && min >= 0;
  const displayMin = isUnitInterval ? 0 : min;
  const displayMax = isUnitInterval ? 100 : max;
  const displayReadings = (readings ?? []).map((r) => ({
    name: r.name,
    value: isUnitInterval ? Math.round(r.value * 1000) / 10 : r.value,
  }));

  const option: echarts.EChartsOption = {
    series: [{
      type: "gauge",
      min: displayMin,
      max: displayMax,
      detail: isUnitInterval ? { formatter: "{value}%" } : undefined,
      data: displayReadings,
    }],
    tooltip: { trigger: "item" },
  };
  return <BaseChart option={option} />;
}

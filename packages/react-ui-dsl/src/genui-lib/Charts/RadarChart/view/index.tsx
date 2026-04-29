"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";
import { buildChartOption, normalizeSeriesItems } from "../../view-utils";

type SeriesItem = { category: string; values: number[] };

type RadarChartViewProps = {
  labels: string[];
  series: SeriesItem[];
};

export function RadarChartView({ labels, series }: RadarChartViewProps) {
  const safeLabels = labels ?? [];
  const safeSeries = normalizeSeriesItems(series as Array<SeriesItem | { type: "element"; props: SeriesItem }>);
  const maxValues = safeLabels.map((_, i) =>
    Math.max(...safeSeries.map(s => s.values[i] ?? 0))
  );
  const option: echarts.EChartsOption = {
    radar: {
      indicator: safeLabels.map((name, i) => ({ name, max: maxValues[i] })),
    },
    series: [{
      type: "radar",
      data: safeSeries.map(s => ({ name: s.category, value: s.values })),
    }],
    legend: {},
    tooltip: {},
  };
  return <BaseChart option={buildChartOption(option)} />;
}

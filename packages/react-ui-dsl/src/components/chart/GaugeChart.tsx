// packages/react-ui-dsl/src/components/chart/GaugeChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";
import { buildChartOption } from "./utils";

interface GaugeChartProps {
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const GaugeChart: React.FC<GaugeChartProps> = ({ options, data, style }) => {
  const option = buildChartOption(options, data);
  return <BaseChart option={option} style={style} />;
};

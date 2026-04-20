// packages/react-ui-dsl/src/components/chart/GaugeChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";
import { buildChartOption } from "./utils";

interface GaugeChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const GaugeChart: React.FC<GaugeChartProps> = ({ properties, data, style }) => {
  const option = buildChartOption(properties, data);
  return <BaseChart option={option} style={style} />;
};

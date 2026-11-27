// packages/react-ui-dsl/src/components/chart/BarChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";
import { buildChartOption } from "./utils";

interface BarChartProps {
  data?: { source: number[][] };
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: React.CSSProperties;
}

export const BarChart: React.FC<BarChartProps> = ({ data, options, style }) => {
  const option = buildChartOption(options, data);
  return <BaseChart option={option} style={style} />;
};

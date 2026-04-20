// packages/react-ui-dsl/src/components/chart/PieChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";
import { buildChartOption } from "./utils";

interface PieChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const PieChart: React.FC<PieChartProps> = ({ properties, data, style }) => {
  const option = buildChartOption(properties, data);
  return <BaseChart option={option} style={style} />;
};

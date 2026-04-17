// packages/react-ui-dsl/src/components/chart/BarChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface BarChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: React.CSSProperties;
}

export const BarChart: React.FC<BarChartProps> = ({ properties, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};

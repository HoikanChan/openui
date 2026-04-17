// packages/react-ui-dsl/src/components/chart/LineChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface LineChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const LineChart: React.FC<LineChartProps> = ({ properties, data, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};

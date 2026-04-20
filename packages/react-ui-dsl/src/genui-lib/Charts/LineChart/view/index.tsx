"use client";

import { LineChart as LineChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";
import type { CSSProperties } from "react";

export type LineChartViewProps = {
  data?: { source: number[][] };
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: CSSProperties;
};

export function LineChartView(props: LineChartViewProps) {
  return (
    <LineChartComponent
      data={props.data}
      properties={props.properties}
      style={props.style}
    />
  );
}

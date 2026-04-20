"use client";

import { PieChart as PieChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";
import type { CSSProperties } from "react";

export type PieChartViewProps = {
  data?: { source: number[][] };
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: CSSProperties;
};

export function PieChartView(props: PieChartViewProps) {
  return (
    <PieChartComponent
      data={props.data}
      properties={props.properties}
      style={props.style}
    />
  );
}

"use client";

import { BarChart as BarChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";
import type { CSSProperties } from "react";

export type BarChartViewProps = {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: CSSProperties;
};

export function BarChartView(props: BarChartViewProps) {
  return <BarChartComponent properties={props.properties} style={props.style} />;
}

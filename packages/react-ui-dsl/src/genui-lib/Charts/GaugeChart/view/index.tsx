"use client";

import { GaugeChart as GaugeChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";
import type { CSSProperties } from "react";

export type GaugeChartViewProps = {
  data?: { source: number[][] };
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: CSSProperties;
};

export function GaugeChartView(props: GaugeChartViewProps) {
  return (
    <GaugeChartComponent
      data={props.data}
      properties={props.properties}
      style={props.style}
    />
  );
}

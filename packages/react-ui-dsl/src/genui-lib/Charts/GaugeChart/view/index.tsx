"use client";

import { GaugeChart as GaugeChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";

export type GaugeChartViewProps = {
  data?: { source: number[][] };
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
};

export function GaugeChartView(props: GaugeChartViewProps) {
  return (
    <GaugeChartComponent
      data={props.data}
      options={props.options}
    />
  );
}

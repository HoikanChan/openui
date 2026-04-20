"use client";

import { BarChart as BarChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";

export type BarChartViewProps = {
  data?: { source: number[][] };
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
};

export function BarChartView(props: BarChartViewProps) {
  return (
    <BarChartComponent
      data={props.data}
      options={props.options}
    />
  );
}

"use client";

import { LineChart as LineChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";

export type LineChartViewProps = {
  data?: { source: number[][] };
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
};

export function LineChartView(props: LineChartViewProps) {
  return (
    <LineChartComponent
      data={props.data}
      options={props.options}
    />
  );
}

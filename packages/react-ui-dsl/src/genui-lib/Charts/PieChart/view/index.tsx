"use client";

import { PieChart as PieChartComponent } from "../../../../components/chart";
import type * as echarts from "echarts";

export type PieChartViewProps = {
  data?: { source: number[][] };
  options?: Omit<echarts.EChartsOption, "title"> & { title?: string };
};

export function PieChartView(props: PieChartViewProps) {
  return (
    <PieChartComponent
      data={props.data}
      options={props.options}
    />
  );
}

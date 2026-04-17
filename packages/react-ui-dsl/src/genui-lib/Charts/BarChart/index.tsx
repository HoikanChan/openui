"use client";

import { defineComponent } from "@openuidev/react-lang";
import { BarChart as BarChartComponent } from "../../../components/chart";
import { BarChartSchema } from "./schema";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "ECharts bar chart",
  component: ({ props }) => (
    <BarChartComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});

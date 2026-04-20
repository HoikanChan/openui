"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { BarChartSchema } from "./schema";
import { type BarChartViewProps, BarChartView } from "./view";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "ECharts bar chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof BarChartSchema>>) => (
    <BarChartView
      properties={props.properties as BarChartViewProps["properties"]}
      style={props.style as React.CSSProperties}
    />
  ),
});

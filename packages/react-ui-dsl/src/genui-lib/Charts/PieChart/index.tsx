"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PieChartSchema } from "./schema";
import { type PieChartViewProps, PieChartView } from "./view";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "ECharts pie chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PieChartSchema>>) => (
    <PieChartView
      data={props.data}
      properties={props.properties as PieChartViewProps["properties"]}
      style={props.style as React.CSSProperties}
    />
  ),
});

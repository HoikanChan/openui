"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { BarChart as BarChartComponent } from "../../../components/chart";
import { z } from "zod";
import { BarChartSchema } from "./schema";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "ECharts bar chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof BarChartSchema>>) => (
    <BarChartComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});

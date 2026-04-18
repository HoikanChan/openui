"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { PieChart as PieChartComponent } from "../../../components/chart";
import { z } from "zod";
import { PieChartSchema } from "./schema";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "ECharts pie chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PieChartSchema>>) => (
    <PieChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});

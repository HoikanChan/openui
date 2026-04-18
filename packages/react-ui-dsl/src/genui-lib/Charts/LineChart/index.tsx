"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { LineChart as LineChartComponent } from "../../../components/chart";
import { z } from "zod";
import { LineChartSchema } from "./schema";

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description: "ECharts line chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LineChartSchema>>) => (
    <LineChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});

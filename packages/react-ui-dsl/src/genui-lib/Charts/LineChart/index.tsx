"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { LineChartSchema } from "./schema";
import { type LineChartViewProps, LineChartView } from "./view";

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description: "ECharts line chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LineChartSchema>>) => (
    <LineChartView
      data={props.data}
      properties={props.properties as LineChartViewProps["properties"]}
      style={props.style as React.CSSProperties}
    />
  ),
});

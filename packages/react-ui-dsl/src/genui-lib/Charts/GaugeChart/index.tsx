"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { GaugeChartSchema } from "./schema";
import { type GaugeChartViewProps, GaugeChartView } from "./view";

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description: "ECharts gauge chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof GaugeChartSchema>>) => (
    <GaugeChartView
      data={props.data}
      properties={props.properties as GaugeChartViewProps["properties"]}
      style={props.style as React.CSSProperties}
    />
  ),
});

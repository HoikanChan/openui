"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { GaugeChart as GaugeChartComponent } from "../../../components/chart";
import { z } from "zod";
import { GaugeChartSchema } from "./schema";

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description: "ECharts gauge chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof GaugeChartSchema>>) => (
    <GaugeChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});

"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { GaugeChartSchema } from "./schema";
import { GaugeChartView } from "./view";

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description: "Gauge dials; use for KPI status, utilization %, or health score — supports multiple needles",
  component: ({ props }: ComponentRenderProps<z.infer<typeof GaugeChartSchema>>) => (
    <GaugeChartView {...props} />
  ),
});

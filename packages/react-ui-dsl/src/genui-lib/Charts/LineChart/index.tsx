"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { LineChartSchema } from "./schema";
import { LineChartView } from "./view";

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description: "Lines over time; use for latency, throughput, or any time-series metric",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LineChartSchema>>) => (
    <LineChartView {...props} />
  ),
});

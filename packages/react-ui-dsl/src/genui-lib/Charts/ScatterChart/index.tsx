"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ScatterChartSchema } from "./schema";
import { ScatterChartView } from "./view";

export const ScatterChart = defineComponent({
  name: "ScatterChart",
  props: ScatterChartSchema,
  description: "Scatter points; use for correlations like latency vs packet loss or throughput vs error rate",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ScatterChartSchema>>) => (
    <ScatterChartView {...props} />
  ),
});

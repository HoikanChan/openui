"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { AreaChartSchema } from "./schema";
import { AreaChartView } from "./view";

export const AreaChart = defineComponent({
  name: "AreaChart",
  props: AreaChartSchema,
  description: "Filled area under lines; use for bandwidth utilization, cumulative traffic, or capacity trends",
  component: ({ props }: ComponentRenderProps<z.infer<typeof AreaChartSchema>>) => (
    <AreaChartView {...props} />
  ),
});

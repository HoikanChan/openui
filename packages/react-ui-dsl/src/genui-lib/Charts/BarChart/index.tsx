"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { BarChartSchema } from "./schema";
import { BarChartView } from "./view";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "Vertical bars; use for comparing values across categories or devices",
  component: ({ props }: ComponentRenderProps<z.infer<typeof BarChartSchema>>) => (
    <BarChartView {...props} />
  ),
});

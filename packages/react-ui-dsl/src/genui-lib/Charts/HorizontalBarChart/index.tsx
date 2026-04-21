"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { HorizontalBarChartSchema } from "./schema";
import { HorizontalBarChartView } from "./view";

export const HorizontalBarChart = defineComponent({
  name: "HorizontalBarChart",
  props: HorizontalBarChartSchema,
  description: "Horizontal bars; use for long category labels, ranked lists, or interface throughput comparison",
  component: ({ props }: ComponentRenderProps<z.infer<typeof HorizontalBarChartSchema>>) => (
    <HorizontalBarChartView {...props} />
  ),
});

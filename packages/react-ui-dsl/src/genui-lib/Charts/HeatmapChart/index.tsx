"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { HeatmapChartSchema } from "./schema";
import { HeatmapChartView } from "./view";

export const HeatmapChart = defineComponent({
  name: "HeatmapChart",
  props: HeatmapChartSchema,
  description: "Color-coded grid; use for traffic patterns by hour/day or alert frequency heatmaps",
  component: ({ props }: ComponentRenderProps<z.infer<typeof HeatmapChartSchema>>) => (
    <HeatmapChartView {...props} />
  ),
});

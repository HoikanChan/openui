"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { RadarChartSchema } from "./schema";
import { RadarChartView } from "./view";

export const RadarChart = defineComponent({
  name: "RadarChart",
  props: RadarChartSchema,
  description: "Radar/spider chart; use for comparing multiple metrics across devices or interfaces",
  component: ({ props }: ComponentRenderProps<z.infer<typeof RadarChartSchema>>) => (
    <RadarChartView {...props} />
  ),
});

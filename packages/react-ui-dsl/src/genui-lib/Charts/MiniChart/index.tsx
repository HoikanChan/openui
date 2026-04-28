"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { MiniChartSchema } from "./schema";
import { MiniChartView } from "./view";

export const MiniChart = defineComponent({
  name: "MiniChart",
  props: MiniChartSchema,
  description: "Compact single-series trend primitive for KPI cards, tables, and dense summaries",
  component: ({ props }: ComponentRenderProps<z.infer<typeof MiniChartSchema>>) => (
    <MiniChartView {...props} />
  ),
});

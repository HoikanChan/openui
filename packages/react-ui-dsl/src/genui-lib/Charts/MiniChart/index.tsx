"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { MiniChartSchema } from "./schema";
import { MiniChartView } from "./view";

export const MiniChart = defineComponent({
  name: "MiniChart",
  props: MiniChartSchema,
  description:
    "Compact single-series trend primitive for KPI cards, table cells, and dense summaries. Width always fills the container; height auto-scales unless height is provided.",
  component: ({ props }: ComponentRenderProps<z.infer<typeof MiniChartSchema>>) => (
    <MiniChartView {...props} />
  ),
});

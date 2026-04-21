"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PieChartSchema } from "./schema";
import { PieChartView } from "./view";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "Circular slices; use for protocol distribution or traffic breakdown by source",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PieChartSchema>>) => (
    <PieChartView {...props} />
  ),
});

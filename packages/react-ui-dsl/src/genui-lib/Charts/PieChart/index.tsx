"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { PieChartSchema } from "./schema";
import { PieChartView } from "./view";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description:
    "Circular slices; values support numbers or percentage strings (e.g., ['70%','30%']) when isPercentage: true.",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PieChartSchema>>) => (
    <PieChartView {...props} />
  ),
});

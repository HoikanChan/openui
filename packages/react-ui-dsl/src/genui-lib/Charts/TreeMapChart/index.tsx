"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TreeMapChartSchema } from "./schema";
import { TreeMapChartView } from "./view";

export const TreeMapChart = defineComponent({
  name: "TreeMapChart",
  props: TreeMapChartSchema,
  description: "Proportional rectangles; use for bandwidth or resource breakdown by subnet or device group",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TreeMapChartSchema>>) => (
    <TreeMapChartView {...props} />
  ),
});

"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { BarChartSchema } from "./schema";
import { BarChartView } from "./view";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "ECharts bar chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof BarChartSchema>>) => {
    const { data, ...options } = props;
    return (
      <BarChartView
        data={data}
        options={options}
      />
    );
  },
});

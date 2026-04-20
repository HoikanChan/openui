"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PieChartSchema } from "./schema";
import { PieChartView } from "./view";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "ECharts pie chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PieChartSchema>>) => {
    const { data, ...options } = props;
    return (
      <PieChartView
        data={data}
        options={options}
      />
    );
  },
});

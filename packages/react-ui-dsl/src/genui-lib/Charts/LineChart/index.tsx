"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { LineChartSchema } from "./schema";
import { LineChartView } from "./view";

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description: "ECharts line chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LineChartSchema>>) => {
    const { data, ...options } = props;
    return (
      <LineChartView
        data={data}
        options={options}
      />
    );
  },
});

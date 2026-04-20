"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { GaugeChartSchema } from "./schema";
import { GaugeChartView } from "./view";

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description: "ECharts gauge chart",
  component: ({ props }: ComponentRenderProps<z.infer<typeof GaugeChartSchema>>) => {
    const { data, ...options } = props;
    return (
      <GaugeChartView
        data={data}
        options={options}
      />
    );
  },
});

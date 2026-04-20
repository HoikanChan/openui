import { z } from "zod";

const gaugeChartFields = {
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  title: z.any().optional(),
  legend: z.any().optional(),
  tooltip: z.any().optional(),
  series: z.any().optional(),
  color: z.any().optional(),
};

export const GaugeChartSchema = z.object(gaugeChartFields).catchall(z.any());

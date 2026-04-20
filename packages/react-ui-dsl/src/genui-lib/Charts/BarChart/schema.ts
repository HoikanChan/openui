import { z } from "zod";

const barChartFields = {
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  title: z.any().optional(),
  legend: z.any().optional(),
  tooltip: z.any().optional(),
  xAxis: z.any().optional(),
  yAxis: z.any().optional(),
  series: z.any().optional(),
  grid: z.any().optional(),
  color: z.any().optional(),
};

export const BarChartSchema = z.object(barChartFields).catchall(z.any());

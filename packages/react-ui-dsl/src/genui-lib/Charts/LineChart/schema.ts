import { z } from "zod";

export const LineChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  style: z.record(z.string(), z.any()).optional(),
});

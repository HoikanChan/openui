import { z } from "zod";

export const PieChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  style: z.record(z.string(), z.any()).optional(),
});

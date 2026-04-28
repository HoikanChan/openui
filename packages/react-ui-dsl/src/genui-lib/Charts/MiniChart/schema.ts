import { z } from "zod";

const MiniChartDatumSchema = z.union([
  z.number(),
  z.object({
    value: z.number(),
    label: z.string().optional(),
  }),
]);

export const MiniChartSchema = z
  .object({
    type: z.enum(["line", "bar", "area"]),
    data: z.array(MiniChartDatumSchema),
    height: z.union([z.number(), z.string()]).optional(),
    color: z.string().optional(),
  })
  .strict();

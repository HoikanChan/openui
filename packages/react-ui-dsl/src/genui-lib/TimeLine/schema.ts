import { z } from "zod";

export const StructuredTimeLineItemSchema = z.object({
  content: z.object({
    title: z.string(),
    children: z.array(z.any()),
  }),
  iconType: z.enum(["success", "error", "default"]),
});

export const SimpleTimeLineItemSchema = z.object({
  title: z.string(),
  description: z.any().optional(),
  status: z.enum(["success", "error", "default"]).optional(),
});

export const TimeLineSchema = z.object({
  data: z.array(z.union([StructuredTimeLineItemSchema, SimpleTimeLineItemSchema])),
  title: z.string().optional(),
  id: z.string().optional(),
});

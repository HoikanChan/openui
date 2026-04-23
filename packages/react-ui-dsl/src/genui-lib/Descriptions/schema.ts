import { z } from "zod";

export const DescriptionFormatSchema = z.enum(["date", "dateTime", "time"]);

export const DescFieldSchema = z.object({
  label: z.string(),
  value: z.any(),
  span: z.number().int().positive().optional(),
  format: DescriptionFormatSchema.optional(),
});

export const DescGroupSchema = z.object({
  title: z.string(),
  fields: z.array(DescFieldSchema),
  columns: z.number().int().positive().optional(),
});

export const DescriptionsSchema = z.object({
  items: z.array(z.union([DescFieldSchema, DescGroupSchema])),
  title: z.string().optional(),
  extra: z.any().optional(),
  columns: z.number().int().positive().optional(),
});

export type DescFieldProps = z.infer<typeof DescFieldSchema>;
export type DescGroupProps = z.infer<typeof DescGroupSchema>;
export type DescriptionsProps = z.infer<typeof DescriptionsSchema>;

import { z } from "zod";

export const DescFieldSchema = z
  .object({
    label: z.string(),
    value: z.any(),
    span: z.number().int().positive().optional(),
  })
  .strict();

export const DescGroupSchema = z
  .object({
    title: z.string(),
    fields: z.array(DescFieldSchema),
    columns: z.number().int().positive().optional(),
  })
  .strict();

export const DescriptionsSchema = z
  .object({
    items: z.array(DescFieldSchema),
    border: z.boolean().optional(),
  })
  .strict();

export type DescFieldProps = z.infer<typeof DescFieldSchema>;
export type DescGroupProps = z.infer<typeof DescGroupSchema>;
export type DescriptionsProps = z.infer<typeof DescriptionsSchema>;

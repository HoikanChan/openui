import { z } from "zod";

export const CardHeaderSchema = z
  .object({
    title: z.string().optional(),
    subtitle: z.string().optional(),
    actions: z.array(z.any()).optional(),
  })
  .optional();

export const CardSchema = z.object({
  variant: z.enum(["card", "clear", "sunk"]).optional(),
  width: z.enum(["standard", "full"]).optional(),
  header: CardHeaderSchema,
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});

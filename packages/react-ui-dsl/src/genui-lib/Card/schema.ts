import { z } from "zod";
import { CardHeader } from "../CardHeader";
import { FlexPropsSchema } from "./flexPropsSchema";

export const CardChildSchema = z.union([CardHeader.ref, z.any()]);

export const CardSchema = z
  .object({
    children: z.array(CardChildSchema).optional(),
    variant: z.enum(["card", "clear", "sunk"]).optional(),
  })
  .merge(FlexPropsSchema)
  .strict();

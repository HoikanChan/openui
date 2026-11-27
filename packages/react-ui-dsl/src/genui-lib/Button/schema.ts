import { z } from "zod";

/** Action value evaluated by the runtime; the prompt renders it as `ActionExpression`. */
const actionPropSchema = z.any();
actionPropSchema.register(z.globalRegistry, { id: "ActionExpression" });

export const ButtonSchema = z.object({
  label: z.string(),
  action: actionPropSchema.optional(),
  variant: z.enum(["primary", "secondary", "tertiary"]).optional(),
  type: z.enum(["default", "text"]).optional(),
  size: z.enum(["extra-small", "small", "normal", "large"]).optional(),
  disabled: z.boolean().optional(),
});

export type ButtonProps = z.infer<typeof ButtonSchema>;

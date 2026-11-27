import { reactive } from "@cloudsop/openui-react-lang";
import { z } from "zod";

export const CalloutSchema = z.object({
  variant: z.enum(["info", "warning", "error", "success", "neutral"]),
  title: z.string(),
  description: z.string(),
  visible: reactive(z.boolean().optional()),
});

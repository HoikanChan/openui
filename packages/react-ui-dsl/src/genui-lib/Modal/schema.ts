import { reactive } from "@cloudsop/openui-react-lang";
import { z } from "zod";

export const ModalSchema = z.object({
  title: z.string(),
  children: z.array(z.any()),
  open: reactive(z.boolean()).optional(),
  size: z.enum(["sm", "md", "lg"]).optional(),
});

export type ModalProps = z.infer<typeof ModalSchema>;

"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { TextAreaSchema } from "./schema";
import { TextAreaView } from "./view";

export * from "./schema";

export const TextArea = defineComponent({
  name: "TextArea",
  props: TextAreaSchema,
  description: "Multi-line text input",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextAreaSchema>>) => (
    <TextAreaView placeholder={props.placeholder} rows={props.rows} value={props.value} />
  ),
});

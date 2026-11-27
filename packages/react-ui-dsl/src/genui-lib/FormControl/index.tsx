"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { FormControlSchema } from "./schema";
import { FormControlView } from "./view";

export * from "./schema";

export const FormControl = defineComponent({
  name: "FormControl",
  props: FormControlSchema,
  description: "Field with name, input component, label text and optional hint text",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof FormControlSchema>>) => (
    <FormControlView
      hint={props.hint}
      input={renderNode(props.input)}
      label={props.label}
      name={props.name}
      required={props.rules?.required}
    />
  ),
});

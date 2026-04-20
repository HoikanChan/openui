"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { FormSchema } from "./schema";
import { FormView } from "./view";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description: "Form with inline field definitions",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof FormSchema>>) => (
    <FormView
      fields={props.fields.map((field: z.infer<typeof FormSchema>["fields"][number]) => ({
        component: renderNode(field.component),
        label: field.label,
        name: field.name,
        required: field.rules?.some((rule: { required: boolean }) => rule.required),
      }))}
      initialValues={props.initialValues}
      labelAlign={props.labelAlign}
      layout={props.layout}
    />
  ),
});

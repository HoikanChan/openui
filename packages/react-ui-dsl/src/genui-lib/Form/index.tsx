"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { FormSchema } from "./schema";
import { FormView } from "./view";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description: "Form container with fields and explicit action buttons",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof FormSchema>>) => (
    <FormView
      buttons={renderNode(props.buttons)}
      fields={props.fields ? renderNode(props.fields) : undefined}
      initialValues={props.initialValues}
      labelAlign={props.labelAlign}
      layout={props.layout}
      name={props.name}
    />
  ),
});

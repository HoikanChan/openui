"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Form as AntForm } from "antd";
import { z } from "zod";
import { FormSchema } from "./schema";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description: "Form with inline field definitions",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof FormSchema>>) => {
    const { layout = "vertical", labelAlign, initialValues, fields } = props.properties;
    return (
      <AntForm
        layout={layout}
        labelAlign={labelAlign}
        initialValues={initialValues}
      >
        {fields.map((field: z.infer<typeof FormSchema>["properties"]["fields"][number], i: number) => (
          <AntForm.Item
            key={i}
            label={field.label}
            name={field.name}
            rules={field.rules?.map((r: { required: boolean }) => ({ required: r.required }))}
          >
            {renderNode(field.component)}
          </AntForm.Item>
        ))}
      </AntForm>
    );
  },
});

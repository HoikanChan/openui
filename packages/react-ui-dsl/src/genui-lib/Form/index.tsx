"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Form as AntForm } from "antd";
import { FormSchema } from "./schema";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description: "Form with inline field definitions",
  component: ({ props, renderNode }) => {
    const { layout = "vertical", labelAlign, initialValues, fields } = props.properties;
    return (
      <AntForm
        layout={layout}
        labelAlign={labelAlign}
        initialValues={initialValues}
      >
        {fields.map((field, i) => (
          <AntForm.Item
            key={i}
            label={field.label}
            name={field.name}
            rules={field.rules?.map((r) => ({ required: r.required }))}
          >
            {renderNode(field.component)}
          </AntForm.Item>
        ))}
      </AntForm>
    );
  },
});

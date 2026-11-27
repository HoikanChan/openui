"use client";

import { Form as AntForm } from "antd";
import type { FormViewProps } from "./types";

export function FormView({
  fields,
  initialValues,
  labelAlign,
  layout = "vertical",
}: FormViewProps) {
  return (
    <AntForm initialValues={initialValues} labelAlign={labelAlign} layout={layout}>
      {fields.map((field) => (
        <AntForm.Item
          key={field.name}
          label={field.label}
          name={field.name}
          rules={field.required ? [{ required: true }] : undefined}
        >
          {field.component}
        </AntForm.Item>
      ))}
    </AntForm>
  );
}

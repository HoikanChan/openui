"use client";

import { Form as AntForm } from "antd";
import type { ReactNode } from "react";

export type FormFieldView = {
  component: ReactNode;
  label: string;
  name: string;
  required?: boolean;
};

export type FormViewProps = {
  fields: FormFieldView[];
  initialValues?: Record<string, unknown>;
  labelAlign?: "left" | "right";
  layout?: "vertical" | "inline" | "horizontal";
};

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

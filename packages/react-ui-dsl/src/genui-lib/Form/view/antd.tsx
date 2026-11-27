"use client";

import { Form as AntForm } from "antd";
import type { FormViewProps } from "./types";

export function FormView({
  buttons,
  fields,
  initialValues,
  labelAlign,
  layout = "vertical",
  name,
}: FormViewProps) {
  return (
    <AntForm initialValues={initialValues} labelAlign={labelAlign} layout={layout} name={name}>
      {fields}
      <div data-form-actions style={{ display: "flex", gap: 8, marginTop: 16 }}>
        {buttons}
      </div>
    </AntForm>
  );
}

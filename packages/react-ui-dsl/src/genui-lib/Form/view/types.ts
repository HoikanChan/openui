"use client";

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

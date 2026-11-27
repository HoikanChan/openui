"use client";

import type { ReactNode } from "react";

export type FormViewProps = {
  name: string;
  buttons: ReactNode;
  fields?: ReactNode;
  initialValues?: Record<string, unknown>;
  labelAlign?: "left" | "right";
  layout?: "vertical" | "inline" | "horizontal";
};

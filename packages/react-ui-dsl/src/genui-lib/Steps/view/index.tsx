"use client";

import type { ReactNode } from "react";

export type StepsViewProps = {
  children?: ReactNode;
};

export function StepsView({ children }: StepsViewProps) {
  return <ol style={{ margin: 0, paddingInlineStart: 20 }}>{children}</ol>;
}

export type StepItemViewProps = {
  title: string;
  details?: ReactNode;
};

export function StepItemView({ details, title }: StepItemViewProps) {
  return (
    <li style={{ marginBottom: 8 }}>
      <span style={{ fontWeight: 600 }}>{title}</span>
      {details == null ? null : <div style={{ marginTop: 4 }}>{details}</div>}
    </li>
  );
}

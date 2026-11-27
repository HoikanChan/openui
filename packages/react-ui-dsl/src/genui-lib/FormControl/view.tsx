"use client";

import { Typography } from "antd";
import type { ReactNode } from "react";

export type FormControlViewProps = {
  name: string;
  label: string;
  input: ReactNode;
  hint?: string;
  required?: boolean;
};

export function FormControlView({ name, label, input, hint, required }: FormControlViewProps) {
  return (
    <div
      data-form-control={name}
      style={{ display: "flex", flexDirection: "column", gap: 6, width: "100%" }}
    >
      <label htmlFor={name} style={{ fontSize: 14 }}>
        {label}
        {required ? (
          <span aria-hidden="true" style={{ color: "#ff4d4f", marginInlineStart: 4 }}>
            *
          </span>
        ) : null}
      </label>
      <div>{input}</div>
      {hint ? (
        <Typography.Text style={{ fontSize: 12 }} type="secondary">
          {hint}
        </Typography.Text>
      ) : null}
    </div>
  );
}

"use client";

import { Input } from "antd";

export type TextAreaViewProps = {
  rows?: number;
  placeholder?: string;
  value?: string;
};

export function TextAreaView({ rows = 3, placeholder, value }: TextAreaViewProps) {
  return (
    <Input.TextArea
      defaultValue={value}
      placeholder={placeholder}
      rows={rows}
      style={{ width: "100%" }}
    />
  );
}

"use client";

import { Select as AntSelect } from "antd";
import type { CSSProperties } from "react";

export type SelectOption = {
  label: string;
  value: number | string;
};

export type SelectViewProps = {
  allowClear?: boolean;
  defaultValue?: number | string;
  options: SelectOption[];
  style?: CSSProperties;
};

export function SelectView(props: SelectViewProps) {
  return (
    <AntSelect
      allowClear={props.allowClear}
      defaultValue={props.defaultValue}
      options={props.options}
      style={{ width: "100%", ...props.style }}
    />
  );
}

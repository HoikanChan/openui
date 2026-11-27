"use client";

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

"use client";

import type { CSSProperties } from "react";

export type SelectOption = {
  label: string;
  value: string;
};

export type SelectViewProps = {
  items?: SelectOption[];
  placeholder?: string;
  value?: string;
  style?: CSSProperties;
};

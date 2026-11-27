"use client";

import type { CSSProperties } from "react";

export type InputSize = "small" | "medium" | "large";

export type InputViewProps = {
  placeholder?: string;
  defaultValue?: string | number;
  disabled?: boolean;
  readOnly?: boolean;
  size?: InputSize;
  hasError?: boolean;
  maxLength?: number;
  style?: CSSProperties;
};

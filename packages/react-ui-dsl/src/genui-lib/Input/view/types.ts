"use client";

import type { CSSProperties } from "react";

export type InputSize = "small" | "medium" | "large";

export type InputType = "text" | "password" | "email" | "number" | "tel" | "url" | "search";

export type InputViewProps = {
  placeholder?: string;
  defaultValue?: string | number;
  disabled?: boolean;
  readOnly?: boolean;
  size?: InputSize;
  hasError?: boolean;
  type?: InputType;
  maxLength?: number;
  style?: CSSProperties;
};

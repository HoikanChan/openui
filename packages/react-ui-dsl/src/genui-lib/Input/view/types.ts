"use client";

import type { CSSProperties } from "react";

export type InputType = "text" | "email" | "password" | "number" | "url";

export type InputViewProps = {
  placeholder?: string;
  type?: InputType;
  value?: string;
  style?: CSSProperties;
};

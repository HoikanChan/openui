"use client";

import type { CSSProperties, ReactNode } from "react";

export type StackViewProps = {
  children?: ReactNode;
  gap?: string;
  style?: CSSProperties;
  vertical?: boolean;
  wrap?: boolean;
};

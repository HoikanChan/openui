"use client";

import type { CSSProperties, ReactNode } from "react";

export type StackViewProps = {
  children?: ReactNode;
  gap?: number;
  style?: CSSProperties;
  vertical?: boolean;
  wrap?: boolean;
};

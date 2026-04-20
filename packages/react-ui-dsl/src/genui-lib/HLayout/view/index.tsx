"use client";

import { Flex } from "antd";
import type { CSSProperties, ReactNode } from "react";

export type HLayoutViewProps = {
  children?: ReactNode;
  gap?: number;
  style?: CSSProperties;
  wrap?: boolean;
};

export function HLayoutView({ children, gap, style, wrap }: HLayoutViewProps) {
  return (
    <Flex gap={gap} style={style} wrap={wrap ? "wrap" : "nowrap"}>
      {children}
    </Flex>
  );
}

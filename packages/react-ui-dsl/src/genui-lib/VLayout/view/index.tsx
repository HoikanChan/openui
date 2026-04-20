"use client";

import { Flex } from "antd";
import type { CSSProperties, ReactNode } from "react";

export type VLayoutViewProps = {
  children?: ReactNode;
  gap?: number;
  style?: CSSProperties;
};

export function VLayoutView({ children, gap, style }: VLayoutViewProps) {
  return (
    <Flex gap={gap} style={style} vertical>
      {children}
    </Flex>
  );
}

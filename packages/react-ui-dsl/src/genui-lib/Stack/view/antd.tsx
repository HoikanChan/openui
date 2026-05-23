"use client";

import { Flex } from "antd";
import type { StackViewProps } from "./types";

export function StackView({ children, gap, style, vertical, wrap }: StackViewProps) {
  return (
    <Flex gap={gap} style={style} vertical={vertical} wrap={wrap ? "wrap" : undefined}>
      {children}
    </Flex>
  );
}

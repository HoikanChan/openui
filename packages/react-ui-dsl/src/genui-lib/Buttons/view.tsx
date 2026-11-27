"use client";

import { Flex } from "antd";
import type { ReactNode } from "react";

export type ButtonsDirection = "row" | "column";

export type ButtonsViewProps = {
  direction?: ButtonsDirection;
  children: ReactNode;
};

export function ButtonsView({ direction = "row", children }: ButtonsViewProps) {
  return (
    <Flex gap="small" vertical={direction === "column"} wrap={direction === "row"}>
      {children}
    </Flex>
  );
}

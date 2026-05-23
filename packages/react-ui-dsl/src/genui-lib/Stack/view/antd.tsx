"use client";

import type { StackViewProps } from "./types";

export function StackView({ children, gap, style, vertical, wrap }: StackViewProps) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: vertical ? "column" : "row",
        gap,
        flexWrap: wrap ? "wrap" : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

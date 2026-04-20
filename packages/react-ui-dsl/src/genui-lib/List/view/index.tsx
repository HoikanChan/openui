"use client";

import type { CSSProperties, ReactNode } from "react";

export type ListViewProps = {
  header?: string;
  isOrder?: boolean;
  items?: ReactNode[];
  style?: CSSProperties;
};

export function ListView({ header, isOrder, items = [], style }: ListViewProps) {
  const Tag = isOrder ? "ol" : "ul";
  const renderedItems = items.map((item, index) => <li key={index}>{item}</li>);

  return (
    <div style={style}>
      {header && <div style={{ fontWeight: 600, marginBottom: 8 }}>{header}</div>}
      <Tag style={{ margin: 0, paddingLeft: 24 }}>{renderedItems}</Tag>
    </div>
  );
}

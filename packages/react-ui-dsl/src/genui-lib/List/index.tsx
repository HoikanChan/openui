"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ListSchema } from "./schema";

export const List = defineComponent({
  name: "List",
  props: ListSchema,
  description: "Ordered or unordered list",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof ListSchema>>) => {
    const { header, isOrder } = props.properties ?? {};
    const Tag = isOrder ? "ol" : "ul";
    const items = (props.children ?? []).map((child: unknown, i: number) => (
      <li key={i}>{renderNode(child)}</li>
    ));
    return (
      <div style={props.style as React.CSSProperties}>
        {header && <div style={{ fontWeight: 600, marginBottom: 8 }}>{header}</div>}
        {Tag === "ol" ? (
          <ol style={{ paddingLeft: 24, margin: 0 }}>{items}</ol>
        ) : (
          <ul style={{ paddingLeft: 24, margin: 0 }}>{items}</ul>
        )}
      </div>
    );
  },
});

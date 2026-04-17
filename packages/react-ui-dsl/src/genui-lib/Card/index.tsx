"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Card as AntCard } from "antd";
import { CardSchema } from "./schema";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description: "Card container with optional header",
  component: ({ props, renderNode }) => {
    const { header, headerAlign = "left", tag } = props.properties ?? {};
    const title = header ? (
      <span style={{ textAlign: headerAlign as React.CSSProperties["textAlign"] }}>
        {tag ? `[${tag}] ${header}` : header}
      </span>
    ) : undefined;
    return (
      <AntCard title={title} style={props.style as React.CSSProperties}>
        {renderNode(props.children)}
      </AntCard>
    );
  },
});

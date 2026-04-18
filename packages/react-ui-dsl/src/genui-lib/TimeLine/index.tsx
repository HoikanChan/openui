"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Timeline } from "antd";
import { z } from "zod";
import { TimeLineSchema } from "./schema";

const iconColorMap = {
  success: "green",
  error: "red",
  default: "gray",
} as const;

export const TimeLine = defineComponent({
  name: "TimeLine",
  props: TimeLineSchema,
  description: "Timeline with typed items, each containing a DSL children tree",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TimeLineSchema>>) => {
    const title = props.properties?.title;
    const items = props.data.map((item: z.infer<typeof TimeLineSchema>["data"][number]) => ({
      color: iconColorMap[item.iconType],
      children: (
        <>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>{item.content.title}</div>
          {renderNode(item.content.children)}
        </>
      ),
    }));

    return (
      <div style={props.style as React.CSSProperties}>
        {title && <div style={{ fontWeight: 700, marginBottom: 12 }}>{title}</div>}
        <Timeline items={items} />
      </div>
    );
  },
});

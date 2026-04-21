"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { CardSchema } from "./schema";
import { CardView } from "./view";

export { CardSchema } from "./schema";

const gapMap: Record<string, string> = {
  none: "0",
  xs: "var(--openui-space-xs, 4px)",
  s: "var(--openui-space-s, 8px)",
  m: "var(--openui-space-m, 12px)",
  l: "var(--openui-space-l, 16px)",
  xl: "var(--openui-space-xl, 20px)",
  "2xl": "var(--openui-space-2xl, 24px)",
};

const alignMap: Record<string, string> = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  stretch: "stretch",
  baseline: "baseline",
};

const justifyMap: Record<string, string> = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  between: "space-between",
  around: "space-around",
  evenly: "space-evenly",
};

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    'Styled container. variant: "card" (default, elevated) | "sunk" (recessed) | "clear" (transparent). Always full width. Accepts flex layout params (default: direction "column").',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof CardSchema>>) => (
    <CardView
      style={{
        alignItems: props.align ? alignMap[props.align] : undefined,
        flexDirection: props.direction ?? "column",
        flexWrap: props.wrap ? "wrap" : "nowrap",
        gap: gapMap[props.gap ?? "m"] ?? gapMap["m"],
        justifyContent: props.justify ? justifyMap[props.justify] : undefined,
      }}
      variant={props.variant ?? "card"}
    >
      {renderNode(props.children)}
    </CardView>
  ),
});

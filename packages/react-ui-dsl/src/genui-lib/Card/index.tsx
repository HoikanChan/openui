"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { CardSchema } from "./schema";
import { CardView } from "./view";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    "Card container with optional header (title, subtitle) and visual variant (card/clear/sunk)",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof CardSchema>>) => (
    <CardView
      header={
        props.header
          ? {
              subtitle: props.header.subtitle,
              title: props.header.title,
            }
          : undefined
      }
      variant={props.variant}
      width={props.width}
    >
      {renderNode(props.children)}
    </CardView>
  ),
});

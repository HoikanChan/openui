"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ListSchema } from "./schema";
import { ListView } from "./view";

export const List = defineComponent({
  name: "List",
  props: ListSchema,
  description: "Ordered or unordered list",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof ListSchema>>) => (
    <ListView
      header={props.properties?.header}
      isOrder={props.properties?.isOrder}
      items={(props.children ?? []).map((child: unknown) => renderNode(child))}
      style={props.style as React.CSSProperties}
    />
  ),
});

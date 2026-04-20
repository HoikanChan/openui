"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { HLayoutSchema } from "./schema";
import { HLayoutView } from "./view";

export const HLayout = defineComponent({
  name: "HLayout",
  props: HLayoutSchema,
  description: "Horizontal flex layout",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof HLayoutSchema>>) => (
    <HLayoutView
      gap={props.properties?.gap}
      style={props.style as React.CSSProperties}
      wrap={props.properties?.wrap}
    >
      {renderNode(props.children)}
    </HLayoutView>
  ),
});

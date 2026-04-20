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
      gap={props.gap}
      wrap={props.wrap}
    >
      {renderNode(props.children)}
    </HLayoutView>
  ),
});

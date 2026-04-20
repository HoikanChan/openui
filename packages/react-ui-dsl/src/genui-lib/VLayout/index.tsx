"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { VLayoutSchema } from "./schema";
import { VLayoutView } from "./view";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description: "Vertical flex layout and default root container",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof VLayoutSchema>>) => (
    <VLayoutView gap={props.gap}>
      {renderNode(props.children)}
    </VLayoutView>
  ),
});

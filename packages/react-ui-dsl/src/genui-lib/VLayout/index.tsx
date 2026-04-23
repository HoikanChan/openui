"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import type { CSSProperties } from "react";
import { z } from "zod";
import { alignMap, justifyMap, resolveGapPixels } from "../flexPropsSchema";
import { VLayoutSchema } from "./schema";
import { VLayoutView } from "./view";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description: "Vertical flex layout and default root container",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof VLayoutSchema>>) => {
    const style: CSSProperties = {
      alignItems: props.align ? alignMap[props.align] : undefined,
      flexDirection: "column",
      flexWrap: props.wrap ? "wrap" : undefined,
      justifyContent: props.justify ? justifyMap[props.justify] : undefined,
    };

    return (
      <VLayoutView gap={resolveGapPixels(props.gap)} style={style}>
        {renderNode(props.children)}
      </VLayoutView>
    );
  },
});

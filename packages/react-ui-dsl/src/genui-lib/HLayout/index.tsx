"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { alignMap, justifyMap, resolveGapPixels } from "../flexPropsSchema";
import { HLayoutSchema } from "./schema";
import { HLayoutView } from "./view";

export const HLayout = defineComponent({
  name: "HLayout",
  props: HLayoutSchema,
  description: "Horizontal flex layout",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof HLayoutSchema>>) => {
    const style = {
      alignItems: props.align ? alignMap[props.align] : undefined,
      flexDirection: "row",
      justifyContent: props.justify ? justifyMap[props.justify] : undefined,
    };

    return (
      <HLayoutView gap={resolveGapPixels(props.gap)} style={style} wrap={props.wrap}>
        {renderNode(props.children)}
      </HLayoutView>
    );
  },
});

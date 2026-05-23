"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import type { CSSProperties } from "react";
import { z } from "zod";
import { alignMap, justifyMap, resolveGapPixels } from "../flexPropsSchema";
import { StackSchema } from "./schema";
import { StackView } from "./view";

export const Stack = defineComponent({
  name: "Stack",
  props: StackSchema,
  description: "Flex layout container (vertical by default); use direction: \"row\" for horizontal",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StackSchema>>) => {
    const direction = props.direction ?? "column";
    const style: CSSProperties = {
      alignItems: props.align ? alignMap[props.align] : undefined,
      justifyContent: props.justify ? justifyMap[props.justify] : undefined,
    };

    return (
      <StackView gap={resolveGapPixels(props.gap)} style={style} vertical={direction === "column"} wrap={props.wrap}>
        {renderNode(props.children)}
      </StackView>
    );
  },
});

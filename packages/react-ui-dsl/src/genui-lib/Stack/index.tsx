"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import type { CSSProperties } from "react";
import { z } from "zod";
import { alignMap, justifyMap, resolveGapRem } from "../flexPropsSchema";
import { StackSchema } from "./schema";
import { StackView } from "./view";

export const Stack = defineComponent({
  name: "Stack",
  props: StackSchema,
  description: 'Flex container. direction: "row"|"column" (default "column"). gap: "none"|"xs"|"s"|"m"|"l"|"xl"|"2xl" (default "m"). align: "start"|"center"|"end"|"stretch"|"baseline". justify: "start"|"center"|"end"|"between"|"around"|"evenly".',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StackSchema>>) => {
    const direction = props.direction ?? "column";
    const effectiveJustify =
      props.wrap && props.justify === "between" ? "start" : props.justify;
    const style: CSSProperties = {
      alignItems: props.align ? alignMap[props.align] : undefined,
      justifyContent: effectiveJustify ? justifyMap[effectiveJustify] : undefined,
    };

    return (
      <StackView gap={resolveGapRem(props.gap)} style={style} vertical={direction === "column"} wrap={props.wrap}>
        {renderNode(props.children)}
      </StackView>
    );
  },
});

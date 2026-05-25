"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import type { CSSProperties } from "react";
import { z } from "zod";

// ── Schema ────────────────────────────────────────────────────────────────────

export const StackSchema = z.object({
  children: z.array(z.any()).optional(),
  direction: z.enum(["row", "column"]).optional(),
  gap: z.enum(["none", "xs", "s", "m", "l", "xl", "2xl"]).optional(),
  align: z.enum(["start", "center", "end", "stretch", "baseline"]).optional(),
  justify: z.enum(["start", "center", "end", "between", "around", "evenly"]).optional(),
  wrap: z.boolean().optional(),
});

// ── Gap token → rem ───────────────────────────────────────────────────────────

const gapRemMap = {
  none: "0",
  xs: "0.375rem",
  s: "0.5rem",
  m: "0.75rem",
  l: "1.125rem",
  xl: "1.5rem",
  "2xl": "2.25rem",
} as const;

type GapToken = keyof typeof gapRemMap;

function resolveGapRem(gap?: GapToken): string {
  return gap ? gapRemMap[gap] : gapRemMap["m"];
}

// ── Flex value maps ───────────────────────────────────────────────────────────

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

// ── Component ─────────────────────────────────────────────────────────────────

export const Stack = defineComponent({
  name: "Stack",
  props: StackSchema,
  description: 'Flex container. direction: "row"|"column" (default "column"). gap: "none"|"xs"|"s"|"m"|"l"|"xl"|"2xl" (default "m"). align: "start"|"center"|"end"|"stretch"|"baseline". justify: "start"|"center"|"end"|"between"|"around"|"evenly".',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StackSchema>>) => {
    const direction = props.direction ?? "column";
    const effectiveJustify =
      props.wrap && props.justify === "between" ? "start" : props.justify;
    const style: CSSProperties = {
      display: "flex",
      flexDirection: direction === "column" ? "column" : "row",
      gap: resolveGapRem(props.gap),
      flexWrap: props.wrap ? "wrap" : undefined,
      alignItems: props.align ? alignMap[props.align] : undefined,
      justifyContent: effectiveJustify ? justifyMap[effectiveJustify] : undefined,
    };

    return <div style={style}>{renderNode(props.children)}</div>;
  },
});

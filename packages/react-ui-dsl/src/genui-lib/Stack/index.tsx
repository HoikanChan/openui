"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import type { CSSProperties } from "react";
import { z } from "zod";
import styles from "./stack.module.css";

// ── Schema ────────────────────────────────────────────────────────────────────

export const StackSchema = z.object({
  children: z.array(z.any()).optional(),
  direction: z.enum(["row", "column"]).optional(),
  align: z.enum(["center", "stretch"]).optional(),
  justify: z.enum(["start", "center", "between"]).optional(),
  wrap: z.boolean().optional(),
});

// ── Default spacing ───────────────────────────────────────────────────────────

const DEFAULT_GAP_REM = "0.75rem";

// ── Flex value maps ───────────────────────────────────────────────────────────

const alignMap: Record<"center" | "stretch", string> = {
  center: "center",
  stretch: "stretch",
};

const justifyMap: Record<"start" | "center" | "between", string> = {
  start: "flex-start",
  center: "center",
  between: "space-between",
};

// ── Component ─────────────────────────────────────────────────────────────────

export const Stack = defineComponent({
  name: "Stack",
  props: StackSchema,
  description:
    'Flex container. justify defaults to "start" — omit unless you need "center" or "between" for 3+ items. Params: direction "row"|"column" (default "column"), align "center"|"stretch", justify "start"|"center"|"between", wrap boolean.',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StackSchema>>) => {
    const direction = props.direction ?? "column";
    const effectiveJustify = props.wrap && props.justify === "between" ? "start" : props.justify;
    const style: CSSProperties = {
      display: "flex",
      flexDirection: direction === "column" ? "column" : "row",
      gap: DEFAULT_GAP_REM,
      flexWrap: props.wrap ? "wrap" : undefined,
      alignItems: props.align ? alignMap[props.align] : undefined,
      justifyContent: effectiveJustify ? justifyMap[effectiveJustify] : undefined,
    };

    const className =
      direction === "row"
        ? `${styles["nestedStack"]} ${styles["row"]}`
        : styles["nestedStack"];

    return (
      <div className={className} style={style}>
        {renderNode(props.children)}
      </div>
    );
  },
});

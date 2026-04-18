"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Flex } from "antd";
import { z } from "zod";
import { HLayoutSchema } from "./schema";

export const HLayout = defineComponent({
  name: "HLayout",
  props: HLayoutSchema,
  description: "Horizontal flex layout",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof HLayoutSchema>>) => (
    <Flex
      gap={props.properties?.gap}
      wrap={props.properties?.wrap ? "wrap" : "nowrap"}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </Flex>
  ),
});

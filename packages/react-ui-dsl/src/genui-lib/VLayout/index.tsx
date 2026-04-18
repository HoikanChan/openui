"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Flex } from "antd";
import { z } from "zod";
import { VLayoutSchema } from "./schema";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description: "Vertical flex layout — default root container",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof VLayoutSchema>>) => (
    <Flex
      vertical
      gap={props.properties?.gap}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </Flex>
  ),
});

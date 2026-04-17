"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Flex } from "antd";
import { VLayoutSchema } from "./schema";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description: "Vertical flex layout — default root container",
  component: ({ props, renderNode }) => (
    <Flex
      vertical
      gap={props.properties?.gap}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </Flex>
  ),
});

"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ButtonsSchema } from "./schema";
import { ButtonsView } from "./view";

export * from "./schema";

export const Buttons = defineComponent({
  name: "Buttons",
  props: ButtonsSchema,
  description: 'Group of Button components. direction: "row" (default) | "column".',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof ButtonsSchema>>) => (
    <ButtonsView direction={props.direction}>{renderNode(props.buttons)}</ButtonsView>
  ),
});

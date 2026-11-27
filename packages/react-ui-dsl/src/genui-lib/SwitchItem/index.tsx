"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { SwitchItemSchema } from "./schema";
import { SwitchItemView } from "./view";

export * from "./schema";

export const SwitchItem = defineComponent({
  name: "SwitchItem",
  props: SwitchItemSchema,
  description: "Standalone switch toggle with optional label and description",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SwitchItemSchema>>) => (
    <SwitchItemView defaultChecked={props.defaultChecked} label={props.label} name={props.name} />
  ),
});

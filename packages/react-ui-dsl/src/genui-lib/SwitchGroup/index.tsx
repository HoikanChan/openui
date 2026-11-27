"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { SwitchGroupSchema } from "./schema";
import { SwitchGroupView } from "./view";

export * from "./schema";

export const SwitchGroup = defineComponent({
  name: "SwitchGroup",
  props: SwitchGroupSchema,
  description: "Group of switch toggles",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SwitchGroupSchema>>) => (
    <SwitchGroupView
      items={props.items.map((item) => item.props)}
      value={props.value}
      variant={props.variant}
    />
  ),
});

"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { CheckBoxItemSchema } from "./schema";
import { CheckBoxItemView } from "./view";

export * from "./schema";

export const CheckBoxItem = defineComponent({
  name: "CheckBoxItem",
  props: CheckBoxItemSchema,
  description: "Checkbox item with label and optional description",
  component: ({ props }: ComponentRenderProps<z.infer<typeof CheckBoxItemSchema>>) => (
    <CheckBoxItemView
      defaultChecked={props.defaultChecked}
      description={props.description}
      label={props.label}
      name={props.name}
    />
  ),
});

"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { CheckBoxGroupSchema } from "./schema";
import { CheckBoxGroupView } from "./view";

export * from "./schema";

export const CheckBoxGroup = defineComponent({
  name: "CheckBoxGroup",
  props: CheckBoxGroupSchema,
  description: "CheckboxGroup component with items and CheckboxGroup wraps each item",
  component: ({ props }: ComponentRenderProps<z.infer<typeof CheckBoxGroupSchema>>) => (
    <CheckBoxGroupView items={props.items.map((item) => item.props)} value={props.value} />
  ),
});

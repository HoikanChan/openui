"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { SelectSchema } from "./schema";
import { SelectView } from "./view";

export const Select = defineComponent({
  name: "Select",
  props: SelectSchema,
  description: "Dropdown select with named items",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SelectSchema>>) => (
    <SelectView items={props.items} placeholder={props.placeholder} value={props.value} />
  ),
});

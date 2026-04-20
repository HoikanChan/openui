"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { SelectSchema } from "./schema";
import { SelectView } from "./view";

export const Select = defineComponent({
  name: "Select",
  props: SelectSchema,
  description: "Dropdown select",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SelectSchema>>) => (
    <SelectView
      allowClear={props.allowClear}
      defaultValue={props.defaultValue}
      options={props.options}
    />
  ),
});

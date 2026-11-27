"use client";

import { defineComponent } from "@cloudsop/openui-react-lang";
import { SelectItemSchema } from "./schema";

export * from "./schema";

export const SelectItem = defineComponent({
  name: "SelectItem",
  props: SelectItemSchema,
  description: "Option for Select dropdown",
  component: () => null,
});

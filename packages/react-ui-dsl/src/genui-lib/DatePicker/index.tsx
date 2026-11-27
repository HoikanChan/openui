"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { DatePickerSchema } from "./schema";
import { DatePickerView } from "./view";

export * from "./schema";

export const DatePicker = defineComponent({
  name: "DatePicker",
  props: DatePickerSchema,
  description: "Date picker supporting single and range selection",
  component: ({ props }: ComponentRenderProps<z.infer<typeof DatePickerSchema>>) => (
    <DatePickerView mode={props.mode} />
  ),
});

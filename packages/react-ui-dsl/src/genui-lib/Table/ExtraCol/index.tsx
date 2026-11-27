"use client";

import { defineComponent } from "@cloudsop/openui-react-lang";
import { ExtraColSchema } from "./schema";

export const ExtraCol = defineComponent({
  name: "ExtraCol",
  props: ExtraColSchema,
  description:
    'Column for a fixed value or action, NOT bound to row fields. Use when a field is at data top-level but NOT in the row array: ExtraCol("设备名", data.neName), ExtraCol("运行状态", data.operStatus), ExtraCol("厂商", data.opticalVendor). For action buttons: ExtraCol("操作", @Render("row", Button(row.name))).',
  component: () => null,
});

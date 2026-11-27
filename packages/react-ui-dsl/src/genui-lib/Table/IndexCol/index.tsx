"use client";

import { defineComponent } from "@cloudsop/openui-react-lang";
import { IndexColSchema } from "./schema";

export const IndexCol = defineComponent({
  name: "IndexCol",
  props: IndexColSchema,
  description: 'Row-number column for Table. IndexCol("序号") — title is required.',
  component: () => null,
});

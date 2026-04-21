"use client";

import { defineComponent } from "@openuidev/react-lang";
import { CardHeaderSchema } from "./schema";
import { CardHeaderView } from "./view";

export { CardHeaderSchema } from "./schema";

export const CardHeader = defineComponent({
  name: "CardHeader",
  props: CardHeaderSchema,
  description: "Header with optional title and subtitle",
  component: ({ props }) => (
    <CardHeaderView
      subtitle={props.subtitle}
      title={props.title}
    />
  ),
});

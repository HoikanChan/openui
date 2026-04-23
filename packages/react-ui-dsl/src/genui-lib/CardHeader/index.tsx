"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { CardHeaderSchema } from "./schema";
import { CardHeaderView } from "./view";

export { CardHeaderSchema } from "./schema";

export const CardHeader = defineComponent({
  name: "CardHeader",
  props: CardHeaderSchema,
  description: "Header with optional title and subtitle",
  component: ({ props }: ComponentRenderProps<z.infer<typeof CardHeaderSchema>>) => (
    <CardHeaderView
      subtitle={props.subtitle}
      title={props.title}
    />
  ),
});

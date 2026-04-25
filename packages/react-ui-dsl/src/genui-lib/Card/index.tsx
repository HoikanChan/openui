"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { CardSchema } from "./schema";
import { CardView } from "./view";

export { CardSchema } from "./schema";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    'Styled container. variant: "card" (default, elevated) | "sunk" (recessed) | "clear" (transparent). width: "standard" (default) | "full". Accepts any DSL component as children. For internal layout (spacing, direction), wrap children in VLayout or HLayout.',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof CardSchema>>) => (
    <CardView variant={props.variant ?? "card"} width={props.width ?? "standard"}>
      {renderNode(props.children)}
    </CardView>
  ),
});

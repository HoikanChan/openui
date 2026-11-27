"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { CardSchema } from "./schema";
import { CardView } from "./view";

export { CardSchema } from "./schema";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    'Styled container. Always full width. Accepts Stack flex params (default: direction "column"). Cards flex to share space in row/wrap layouts.',
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof CardSchema>>) => (
    <CardView
      align={props.align}
      direction={props.direction}
      justify={props.justify}
      wrap={props.wrap}
    >
      {renderNode(props.children)}
    </CardView>
  ),
});

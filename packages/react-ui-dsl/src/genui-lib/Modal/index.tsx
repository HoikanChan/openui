"use client";

import {
  type ComponentRenderProps,
  defineComponent,
  useStateField,
} from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ModalSchema } from "./schema";
import { ModalView } from "./view";

export * from "./schema";
export * from "./view";

export const Modal = defineComponent({
  name: "Modal",
  props: ModalSchema,
  description:
    "Modal dialog. open is a reactive $boolean binding — set to true to open, X/Escape/backdrop auto-closes. Put Form with buttons inside children.",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof ModalSchema>>) => {
    const open = useStateField("open", props.open);

    return (
      <ModalView
        onClose={() => open.setValue(false)}
        open={open.value === true}
        size={props.size}
        title={props.title}
      >
        {renderNode(props.children)}
      </ModalView>
    );
  },
});

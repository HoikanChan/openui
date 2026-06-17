"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { PiuSchema } from "./schema";

export { PiuSchema } from "./schema";
export type { PiuProps } from "./schema";

export const Piu = defineComponent({
  name: "Piu",
  props: PiuSchema,
  description: "Piu component placeholder — implementation to be added later",
  component: ({ props }: ComponentRenderProps<z.infer<typeof PiuSchema>>) => (
    <div data-piu-name={props.name} data-piu-event={props.eventName} data-piu-destroy={props.destroy}>
      {props.name}
    </div>
  ),
});

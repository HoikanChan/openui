"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { SliderSchema } from "./schema";
import { SliderView } from "./view";

export * from "./schema";

export const Slider = defineComponent({
  name: "Slider",
  props: SliderSchema,
  description: "Numeric slider input; supports continuous and discrete variants",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SliderSchema>>) => (
    <SliderView
      defaultValue={props.defaultValue}
      label={props.label}
      max={props.max}
      min={props.min}
      variant={props.variant}
    />
  ),
});

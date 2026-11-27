"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { StepsItemSchema, StepsSchema } from "./schema";
import { StepItemView, StepsView } from "./view";

export * from "./schema";
export * from "./view";

export const StepsItem = defineComponent({
  name: "StepsItem",
  props: StepsItemSchema,
  description:
    "One step with optional expandable details. Details may contain text, components, or nested StepsItem values.",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StepsItemSchema>>) => {
    const details = props.details;
    const renderedDetails =
      details == null ? undefined : Array.isArray(details) ? (
        <StepsView>{renderNode(details)}</StepsView>
      ) : (
        renderNode(details)
      );

    return <StepItemView details={renderedDetails} title={props.title} />;
  },
});

export const Steps = defineComponent({
  name: "Steps",
  props: StepsSchema,
  description: "Ordered step-by-step procedure. Every item must be a StepsItem.",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof StepsSchema>>) => (
    <StepsView>{renderNode(props.items)}</StepsView>
  ),
});

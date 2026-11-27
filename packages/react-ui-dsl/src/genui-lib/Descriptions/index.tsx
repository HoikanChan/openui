"use client";

import React from "react";
import {
  type ComponentRenderProps,
  defineComponent,
  type ElementNode,
  type SubComponentOf,
} from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { DescFieldSchema, DescriptionsSchema, type DescFieldProps } from "./schema";
import {
  DescriptionsRuntimeView,
  resolveAutoSpan,
  type ResolvedDescriptionsField,
} from "./view";

export * from "./schema";
export * from "./view";

export const DescField = defineComponent({
  name: "DescField",
  props: DescFieldSchema,
  description: "Declarative descriptions field with label, direct value expression, and optional span.",
  component: () => null,
});

function isComponentNode<T>(value: unknown): value is SubComponentOf<T> {
  return typeof value === "object" && value !== null && "type" in value && "props" in value;
}

function getFieldProps(value: unknown): DescFieldProps {
  return isComponentNode<DescFieldProps>(value) ? value.props : DescFieldSchema.parse(value);
}

function isElementNode(value: unknown): value is ElementNode {
  return (
    typeof value === "object" &&
    value !== null &&
    (value as ElementNode).type === "element" &&
    typeof (value as ElementNode).typeName === "string" &&
    typeof (value as ElementNode).props === "object" &&
    (value as ElementNode).props !== null &&
    typeof (value as ElementNode).partial === "boolean"
  );
}

type DescriptionsRenderNode = ComponentRenderProps<z.infer<typeof DescriptionsSchema>>["renderNode"];

export function resolveDescriptionFieldValue(
  field: DescFieldProps,
  renderNode: DescriptionsRenderNode,
  resolvedSpan: number,
): ResolvedDescriptionsField {
  const renderedValue = resolveDescriptionRenderedValue(field.value, renderNode);

  return {
    kind: "field",
    label: field.label,
    renderedValue,
    resolvedSpan,
  };
}

export function resolveDescriptionRenderedValue(
  value: unknown,
  renderNode: DescriptionsRenderNode,
) {
  if (isElementNode(value)) return renderNode(value);
  if (value == null || value === "") return "-";
  return String(value);
}

export const Descriptions = defineComponent({
  name: "Descriptions",
  props: DescriptionsSchema,
  description: "Descriptions-style detail view for a single record with optional bordered and plain visual modes.",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof DescriptionsSchema>>) => (
    <DescriptionsRuntimeView
      border={props.border}
      columns={props.items.length >= 8 ? 2 : undefined}
      items={props.items.map(getFieldProps)}
      renderValue={(field, resolvedSpan) => resolveDescriptionFieldValue(field, renderNode, resolvedSpan)}
    />
  ),
});

export { resolveAutoSpan };

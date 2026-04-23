"use client";

import React from "react";
import {
  type ComponentRenderProps,
  defineComponent,
  type ElementNode,
  type SubComponentOf,
} from "@openuidev/react-lang";
import { z } from "zod";
import {
  DescFieldSchema,
  DescGroupSchema,
  DescriptionsSchema,
  type DescFieldProps,
  type DescGroupProps,
} from "./schema";
import {
  DescriptionsRuntimeView,
  formatDescriptionValue,
  resolveAutoSpan,
  type DescriptionFormat,
  type ResolvedDescriptionsField,
} from "./view";

export * from "./schema";
export * from "./view";

export const DescField = defineComponent({
  name: "DescField",
  props: DescFieldSchema,
  description: "Declarative descriptions field with label, value, optional span, and optional date/time format.",
  component: () => null,
});

export const DescGroup = defineComponent({
  name: "DescGroup",
  props: DescGroupSchema,
  description: "Declarative descriptions group with a title, field list, and optional column override.",
  component: () => null,
});

type DescFieldValue = DescFieldProps | SubComponentOf<DescFieldProps>;
type DescGroupValue = DescGroupProps | SubComponentOf<DescGroupProps>;

function isComponentNode<T>(value: T | SubComponentOf<T>): value is SubComponentOf<T> {
  return typeof value === "object" && value !== null && "type" in value && "props" in value;
}

function getFieldProps(value: DescFieldValue): DescFieldProps {
  return isComponentNode(value) ? value.props : value;
}

function getGroupProps(value: DescGroupValue): DescGroupProps {
  const props = isComponentNode(value) ? value.props : value;
  return {
    ...props,
    fields: props.fields.map((field) => getFieldProps(field as DescFieldValue)),
  };
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

function isGroupNode(value: DescFieldValue | DescGroupValue): value is DescGroupValue {
  const props = isComponentNode(value) ? value.props : value;
  return typeof props === "object" && props !== null && "fields" in props;
}

type DescriptionsRenderNode = ComponentRenderProps<z.infer<typeof DescriptionsSchema>>["renderNode"];

export function resolveDescriptionFieldValue(
  field: DescFieldProps,
  renderNode: DescriptionsRenderNode,
  resolvedSpan: number,
): ResolvedDescriptionsField {
  const renderedValue = resolveDescriptionRenderedValue(field.value, field.format, renderNode);

  return {
    kind: "field",
    label: field.label,
    renderedValue,
    resolvedSpan,
  };
}

export function resolveDescriptionRenderedValue(
  value: unknown,
  format: DescriptionFormat | undefined,
  renderNode: DescriptionsRenderNode,
) {
  if (isElementNode(value)) return renderNode(value);
  return formatDescriptionValue(value, format);
}

export const Descriptions = defineComponent({
  name: "Descriptions",
  props: DescriptionsSchema,
  description: "Metric-card style detail view for a single record using DescField and DescGroup items.",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof DescriptionsSchema>>) => {
    const normalizedItems = props.items.map((item: DescFieldValue | DescGroupValue) =>
      isGroupNode(item)
        ? getGroupProps(item as DescGroupValue)
        : getFieldProps(item as DescFieldValue),
    );

    const extra = isElementNode(props.extra) ? renderNode(props.extra) : props.extra;

    return (
      <DescriptionsRuntimeView
        columns={props.columns}
        extra={extra}
        items={normalizedItems}
        renderValue={(field, resolvedSpan) => resolveDescriptionFieldValue(field, renderNode, resolvedSpan)}
        title={props.title}
      />
    );
  },
});

export { resolveAutoSpan };

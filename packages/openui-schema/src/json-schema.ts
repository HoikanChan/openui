import type { Schema, WrapperDef, OptionalDef, DefaultDef, NullableDef } from "./core";
import type { ObjectDef } from "./object";
import type { StringDef, NumberDef } from "./primitives";
import type { EnumDef } from "./enum";
import type { UnionDef } from "./union";
import type { ArrayDef } from "./array";
import type { RecordDef } from "./record";
import type { LiteralDef } from "./literal";
import type { TupleDef } from "./tuple";
import type { NativeEnumDef } from "./native-enum";
import type { LazyDef } from "./lazy";
import { LazySchema } from "./lazy";

export type JSONSchema = Record<string, unknown>;

export function toJSONSchema(schema: Schema): JSONSchema {
  return convert(schema);
}

function convert(schema: Schema): JSONSchema {
  const def = schema._def;

  switch (def.type) {
    case "object":
      return convertObject(def as ObjectDef);
    case "string":
      return convertString(def as StringDef);
    case "number":
      return convertNumber(def as NumberDef);
    case "boolean":
      return { type: "boolean" };
    case "enum":
      return { enum: (def as EnumDef).values };
    case "nativeEnum":
      return { enum: (def as NativeEnumDef).values };
    case "union":
      return { oneOf: (def as UnionDef).options.map((s: Schema) => convert(s)) };
    case "array":
      return { type: "array", items: convert((def as ArrayDef).element) };
    case "tuple":
      return {
        type: "array",
        items: (def as TupleDef).items.map((s: Schema) => convert(s)),
        additionalItems: false,
      };
    case "record":
      return {
        type: "object",
        additionalProperties: convert((def as RecordDef).valueType),
      };
    case "any":
      return {};
    case "unknown":
      return {};
    case "never":
      return { not: {} };
    case "literal":
      return { const: (def as LiteralDef).values[0] };
    case "date":
      return { type: "string", format: "date-time" };
    case "lazy":
      return convert((schema as LazySchema).innerType);
    case "optional":
      return convert((def as OptionalDef).innerType);
    case "default":
      return convertDefault(def as DefaultDef);
    case "nullable":
      return { oneOf: [convert((def as NullableDef).innerType), { type: "null" }] };
    default:
      return {};
  }
}

function isDeepOptional(schema: Schema): boolean {
  const t = schema._def.type;
  return t === "optional" || t === "default" || t === "nullable";
}

function unwrapToLeaf(schema: Schema): Schema {
  const t = schema._def.type;
  if (t === "optional" || t === "default" || t === "nullable") {
    return unwrapToLeaf((schema._def as WrapperDef).innerType);
  }
  return schema;
}

function collectWrapperMeta(schema: Schema, meta: { hasDefault?: boolean; defaultValue?: unknown; hasNullable?: boolean }): void {
  const t = schema._def.type;
  if (t === "default") {
    meta.hasDefault = true;
    meta.defaultValue = (schema._def as DefaultDef).defaultValue;
    collectWrapperMeta((schema._def as DefaultDef).innerType, meta);
  } else if (t === "nullable") {
    meta.hasNullable = true;
    collectWrapperMeta((schema._def as NullableDef).innerType, meta);
  } else if (t === "optional") {
    collectWrapperMeta((schema._def as OptionalDef).innerType, meta);
  }
}

function convertObject(def: ObjectDef): JSONSchema {
  const properties: Record<string, JSONSchema> = {};
  const required: string[] = [];

  for (const [key, fieldSchema] of Object.entries(def.shape)) {
    const isOptional = isDeepOptional(fieldSchema);
    if (isOptional) {
      const leafSchema = unwrapToLeaf(fieldSchema);
      const fieldJson = convert(leafSchema);
      const meta: { hasDefault?: boolean; defaultValue?: unknown; hasNullable?: boolean } = {};
      collectWrapperMeta(fieldSchema, meta);
      if (meta.hasDefault) {
        fieldJson.default = meta.defaultValue;
      }
      if (meta.hasNullable) {
        properties[key] = { oneOf: [fieldJson, { type: "null" }] };
      } else {
        properties[key] = fieldJson;
      }
    } else {
      properties[key] = convert(fieldSchema);
      required.push(key);
    }
  }

  const result: JSONSchema = { type: "object", properties };

  if (required.length > 0) {
    result.required = required;
  }

  if (def.strict) {
    result.additionalProperties = false;
  }

  return result;
}

function convertString(def: StringDef): JSONSchema {
  const result: JSONSchema = { type: "string" };
  if (def.pattern) {
    result.pattern = def.pattern;
  }
  if (def.format) {
    result.format = def.format;
  }
  return result;
}

function convertNumber(def: NumberDef): JSONSchema {
  const result: JSONSchema = { type: def.int ? "integer" : "number" };

  if (def.positive) {
    result.exclusiveMinimum = 0;
  }

  if (def.min !== undefined) {
    if (def.positive && def.min <= 0) {
      result.exclusiveMinimum = 0;
    } else {
      result.minimum = def.min;
    }
  }

  if (def.max !== undefined) {
    result.maximum = def.max;
  }

  return result;
}

function convertDefault(def: DefaultDef): JSONSchema {
  const innerJson = convert(def.innerType);
  return { ...innerJson, default: def.defaultValue };
}

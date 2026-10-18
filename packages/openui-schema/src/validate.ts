import type { AnySchemaDef } from "./any-schema-def";
import type { Schema, SchemaDef, WrapperDef } from "./core";

export interface SafeParseSuccess<T> {
  success: true;
  data: T;
}

export interface SafeParseError {
  success: false;
  error: { message: string };
}

export type SafeParseResult<T> = SafeParseSuccess<T> | SafeParseError;

function fail(message: string): SafeParseError {
  return { success: false, error: { message } };
}

function ok<T>(data: T): SafeParseSuccess<T> {
  return { success: true, data };
}

function allowsUndefined(schema: Schema): boolean {
  const t = schema._def.type;
  return t === "optional" || t === "default" || t === "nullable";
}

export function validate(schema: Schema, input: unknown): SafeParseResult<any> {
  return validateDef(schema._def as AnySchemaDef, input);
}

function validateDef(def: AnySchemaDef, input: unknown): SafeParseResult<any> {
  switch (def.type) {
    case "string":
      if (typeof input !== "string") return fail(`Expected string, got ${typeof input}`);
      if (def.pattern && !new RegExp(def.pattern).test(input))
        return fail(`String does not match pattern ${def.pattern}`);
      if (def.format === "uri" && !/^https?:\/\/.+/.test(input))
        return fail("String is not a valid URI");
      if (def.format === "email" && !/^[^@]+@[^@]+$/.test(input))
        return fail("String is not a valid email");
      return ok(input);

    case "number":
      if (typeof input !== "number") return fail(`Expected number, got ${typeof input}`);
      if (def.int && !Number.isInteger(input)) return fail("Expected integer");
      if (def.positive && input <= 0) return fail("Expected positive number");
      if (def.min !== undefined && input < def.min) return fail(`Expected number >= ${def.min}`);
      if (def.max !== undefined && input > def.max) return fail(`Expected number <= ${def.max}`);
      return ok(input);

    case "boolean":
      if (typeof input !== "boolean") return fail(`Expected boolean, got ${typeof input}`);
      return ok(input);

    case "object": {
      if (typeof input !== "object" || input === null || Array.isArray(input))
        return fail("Expected object");
      const obj = input as Record<string, unknown>;
      const shape = def.shape as Record<string, Schema>;
      const requiredKeys = Object.entries(shape)
        .filter(([, s]) => !allowsUndefined(s))
        .map(([k]) => k);
      for (const key of requiredKeys) {
        if (!(key in obj)) return fail(`Missing required property "${key}"`);
      }
      if (def.strict) {
        const allowed = Object.keys(shape);
        for (const key of Object.keys(obj)) {
          if (!allowed.includes(key)) return fail(`Unknown property "${key}"`);
        }
      }
      const result: Record<string, unknown> = { ...obj };
      for (const [key, fieldSchema] of Object.entries(shape)) {
        if (key in obj) {
          const r = validate(fieldSchema, obj[key]);
          if (!r.success) return fail(`Property "${key}": ${r.error.message}`);
          result[key] = r.data;
        } else if (fieldSchema._def.type === "default") {
          result[key] = (fieldSchema._def as any).defaultValue;
        }
      }
      return ok(result);
    }

    case "enum":
      if (!def.values.includes(input as string))
        return fail(`Expected one of [${def.values.join(", ")}], got ${input}`);
      return ok(input);

    case "union": {
      for (const option of def.options) {
        const r = validate(option, input);
        if (r.success) return r;
      }
      return fail("No union option matched");
    }

    case "array": {
      if (!Array.isArray(input)) return fail(`Expected array, got ${typeof input}`);
      const arr = [...(input as unknown[])];
      for (let i = 0; i < arr.length; i++) {
        const r = validate(def.element, arr[i]);
        if (!r.success) return fail(`Index ${i}: ${r.error.message}`);
        arr[i] = r.data;
      }
      return ok(arr);
    }

    case "record": {
      if (typeof input !== "object" || input === null || Array.isArray(input))
        return fail("Expected object for record");
      const obj = input as Record<string, unknown>;
      const result: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(obj)) {
        const kr = validate(def.keyType, key);
        if (!kr.success) return fail(`Record key "${key}": ${kr.error.message}`);
        const vr = validate(def.valueType, value);
        if (!vr.success) return fail(`Record value at "${key}": ${vr.error.message}`);
        result[key] = vr.data;
      }
      return ok(result);
    }

    case "any":
      return ok(input);

    case "unknown":
      return ok(input);

    case "never":
      return fail("Expected never");

    case "literal":
      if (input !== def.values[0]) return fail(`Expected literal ${JSON.stringify(def.values[0])}`);
      return ok(input);

    case "tuple": {
      if (!Array.isArray(input)) return fail("Expected array for tuple");
      if (input.length !== def.items.length)
        return fail(`Expected tuple of length ${def.items.length}, got ${input.length}`);
      const result: unknown[] = [];
      for (let i = 0; i < def.items.length; i++) {
        const r = validate(def.items[i], input[i]);
        if (!r.success) return fail(`Tuple index ${i}: ${r.error.message}`);
        result[i] = r.data;
      }
      return ok(result);
    }

    case "date":
      if (!(input instanceof Date)) return fail("Expected Date");
      return ok(input);

    case "nativeEnum": {
      if (!def.values.includes(input as string | number))
        return fail(`Expected native enum value, got ${input}`);
      return ok(input);
    }

    case "lazy": {
      const inner = def.getter();
      return validate(inner, input);
    }

    case "optional":
      if (input === undefined) return ok(undefined);
      return validate(def.innerType, input);

    case "default":
      if (input === undefined) return ok(def.defaultValue);
      const innerResult = validate(def.innerType, input);
      if (innerResult.success) return innerResult;
      return innerResult;

    case "nullable":
      if (input === null) return ok(null);
      return validate(def.innerType, input);

    default:
      return ok(input);
  }
}

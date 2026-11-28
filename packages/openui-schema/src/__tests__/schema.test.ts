import { describe, expect, it } from "vitest";
import {
  z,
  toJSONSchema,
  GlobalRegistry,
  Schema,
  ObjectSchema,
  NumberSchema,
  UnionSchema,
  OptionalSchema,
  DefaultSchema,
  NullableSchema,
  UnknownSchema,
  NeverSchema,
  LiteralSchema,
  TupleSchema,
  DateSchema,
  NativeEnumSchema,
  LazySchema,
  StringSchema,
  ArraySchema,
  EnumSchema,
  RecordSchema,
  AnySchema,
  BooleanSchema,
} from "../index";
import type { Infer, IsAny, ZodType, ZodObject, ZodTypeAny } from "../index";



describe("z.object", () => {
  it("creates object schema with shape", () => {
    const schema = z.object({
      name: z.string(),
      age: z.number(),
    });
    expect(schema._def.type).toBe("object");
    expect(schema.shape).toHaveProperty("name");
    expect(schema.shape).toHaveProperty("age");
  });

  it("supports strict mode", () => {
    const strict = z.object({ x: z.number() }).strict();
    expect(strict._def.strict).toBe(true);
    expect(strict._def.shape).toHaveProperty("x");
  });

  it("supports merge", () => {
    const layoutSchema = z.object({
      direction: z.enum(["row", "column"]),
      gap: z.enum(["none", "xs", "s"]),
    });
    const childSchema = z.object({
      children: z.array(z.any()),
    });
    const merged = layoutSchema.merge(childSchema);
    expect(merged._def.type).toBe("object");
    expect(merged.shape).toHaveProperty("direction");
    expect(merged.shape).toHaveProperty("gap");
    expect(merged.shape).toHaveProperty("children");
  });

  it("merge propagates strict from either side", () => {
    const a = z.object({ x: z.number() }).strict();
    const b = z.object({ y: z.string() });
    const merged = a.merge(b);
    expect(merged._def.strict).toBe(true);
  });

  it("merge overwrites overlapping keys", () => {
    const a = z.object({ x: z.string() });
    const b = z.object({ x: z.number() });
    const merged = a.merge(b);
    expect(merged.shape.x._def.type).toBe("number");
  });
});

describe("z.string / z.number / z.boolean", () => {
  it("creates primitive schemas", () => {
    expect(z.string()._def.type).toBe("string");
    expect(z.number()._def.type).toBe("number");
    expect(z.boolean()._def.type).toBe("boolean");
  });

  it("number.int() and number.positive()", () => {
    const intPos = z.number().int().positive();
    expect(intPos._def.int).toBe(true);
    expect(intPos._def.positive).toBe(true);
  });

  it("number.min() and number.max()", () => {
    const constrained = z.number().min(1).max(12);
    expect(constrained._def.min).toBe(1);
    expect(constrained._def.max).toBe(12);
  });

  it("number.int().min().max() chains", () => {
    const schema = z.number().int().min(1).max(12);
    expect(schema._def.int).toBe(true);
    expect(schema._def.min).toBe(1);
    expect(schema._def.max).toBe(12);
  });

  it("string.regex()", () => {
    const schema = z.string().regex(/^\d+$/);
    expect(schema._def.pattern).toBe("^\\d+$");
  });

  it("string.regex() with string pattern", () => {
    const schema = z.string().regex("^\\d+$");
    expect(schema._def.pattern).toBe("^\\d+$");
  });

  it("string.url()", () => {
    expect(z.string().url()._def.format).toBe("uri");
  });

  it("string.email()", () => {
    expect(z.string().email()._def.format).toBe("email");
  });
});

describe("z.enum", () => {
  it("creates enum schema", () => {
    const schema = z.enum(["a", "b", "c"]);
    expect(schema._def.type).toBe("enum");
    expect(schema._def.values).toEqual(["a", "b", "c"]);
  });
});

describe("z.union", () => {
  it("creates union with options", () => {
    const schema = z.union([z.string(), z.number()]);
    expect(schema._def.type).toBe("union");
    expect(schema.options.length).toBe(2);
  });

  it("spreads options from another union", () => {
    const base = z.union([z.string(), z.number()]);
    const extended = z.union([...base.options, z.boolean()]);
    expect(extended.options.length).toBe(3);
  });
});

describe("z.array", () => {
  it("creates array schema", () => {
    const schema = z.array(z.string());
    expect(schema._def.type).toBe("array");
    expect(schema._def.element._def.type).toBe("string");
  });
});

describe("z.record", () => {
  it("creates record schema", () => {
    const schema = z.record(z.string(), z.any());
    expect(schema._def.type).toBe("record");
    expect(schema._def.keyType._def.type).toBe("string");
    expect(schema._def.valueType._def.type).toBe("any");
  });
});

describe("z.any", () => {
  it("creates any schema", () => {
    expect(z.any()._def.type).toBe("any");
  });
});

describe("z.unknown", () => {
  it("creates unknown schema", () => {
    const schema = z.unknown();
    expect(schema._def.type).toBe("unknown");
  });
});

describe("z.never", () => {
  it("creates never schema", () => {
    const schema = z.never();
    expect(schema._def.type).toBe("never");
  });
});

describe("z.literal", () => {
  it("creates literal schema with string value", () => {
    const schema = z.literal("admin");
    expect(schema._def.type).toBe("literal");
    expect(schema._def.values).toEqual(["admin"]);
  });

  it("creates literal schema with number value", () => {
    const schema = z.literal(42);
    expect(schema._def.values).toEqual([42]);
  });
});

describe("z.tuple", () => {
  it("creates tuple schema", () => {
    const schema = z.tuple([z.string(), z.number()]);
    expect(schema._def.type).toBe("tuple");
    expect(schema._def.items.length).toBe(2);
    expect(schema._def.items[0]._def.type).toBe("string");
    expect(schema._def.items[1]._def.type).toBe("number");
  });
});

describe("z.date", () => {
  it("creates date schema", () => {
    const schema = z.date();
    expect(schema._def.type).toBe("date");
  });
});

describe("z.nativeEnum", () => {
  it("creates nativeEnum schema from string enum object", () => {
    const schema = z.nativeEnum({ A: "a", B: "b" });
    expect(schema._def.type).toBe("nativeEnum");
    expect(schema._def.values).toEqual(["a", "b"]);
  });

  it("creates nativeEnum schema from numeric enum object", () => {
    const schema = z.nativeEnum({ A: 1, B: 2 });
    expect(schema._def.type).toBe("nativeEnum");
    expect(schema._def.values).toEqual([1, 2]);
  });
});

describe("z.lazy", () => {
  it("creates lazy schema with getter", () => {
    const inner = z.string();
    const schema = z.lazy(() => inner);
    expect(schema._def.type).toBe("lazy");
    expect(schema.innerType._def.type).toBe("string");
  });

  it("caches inner schema", () => {
    let callCount = 0;
    const schema = z.lazy(() => {
      callCount++;
      return z.string();
    });
    schema.innerType;
    schema.innerType;
    expect(callCount).toBe(1);
  });
});

describe(".optional()", () => {
  it("wraps schema as optional", () => {
    const opt = z.string().optional();
    expect(opt._def.type).toBe("optional");
    expect(opt._def.innerType._def.type).toBe("string");
  });

  it("optional field is not required in JSON Schema", () => {
    const schema = z.object({
      name: z.string(),
      age: z.number().optional(),
      abc: z.object({
        name: z.string(),
        age: z.number().optional(),
      }),
    });
    const json = toJSONSchema(schema);
    expect(json.required).toEqual(["name", "abc"]);
    expect(json.properties).toHaveProperty("age");
  });
});

describe(".default()", () => {
  it("wraps schema with default value", () => {
    const schema = z.string().default("hello");
    expect(schema._def.type).toBe("default");
    expect(schema._def.defaultValue).toBe("hello");
    expect(schema._def.innerType._def.type).toBe("string");
  });

  it("default field is not required in JSON Schema", () => {
    const schema = z.object({
      name: z.string(),
      role: z.string().default("user"),
    });
    const json = toJSONSchema(schema);
    expect(json.required).toEqual(["name"]);
    expect(json.properties.role).toEqual({
      type: "string",
      default: "user",
    });
  });

  it("array default", () => {
    const schema = z.array(z.any()).default([]);
    expect(schema._def.defaultValue).toEqual([]);
    expect(schema._def.innerType._def.type).toBe("array");
  });

  it("object default", () => {
    const schema = z.object({ w: z.number() }).default({ w: 6 });
    expect(schema._def.defaultValue).toEqual({ w: 6 });
  });
});

describe(".nullable()", () => {
  it("wraps schema as nullable", () => {
    const schema = z.string().nullable();
    expect(schema._def.type).toBe("nullable");
    expect(schema._def.innerType._def.type).toBe("string");
  });

  it("nullable field is not required in JSON Schema and produces oneOf", () => {
    const schema = z.object({
      name: z.string(),
      note: z.string().nullable(),
    });
    const json = toJSONSchema(schema);
    expect(json.required).toEqual(["name"]);
    expect(json.properties.note).toEqual({
      oneOf: [{ type: "string" }, { type: "null" }],
    });
  });
});

describe(".register() / globalRegistry", () => {
  it("registers and retrieves schema id", () => {
    const registry = new GlobalRegistry();
    const schema = z.object({ label: z.string() });
    schema.register(registry, { id: "Button" });
    expect(registry.get(schema)).toEqual({ id: "Button" });
    expect(registry.has(schema)).toBe(true);
  });

  it("z.globalRegistry singleton works", () => {
    const schema = z.object({ title: z.string() });
    schema.register(z.globalRegistry, { id: "Card" });
    expect(z.globalRegistry.get(schema)).toEqual({ id: "Card" });
  });
});

describe("toJSONSchema", () => {
  it("converts simple object", () => {
    const schema = z.object({
      label: z.string(),
      count: z.number(),
      active: z.boolean(),
    });
    const json = toJSONSchema(schema);
    expect(json).toEqual({
      type: "object",
      properties: {
        label: { type: "string" },
        count: { type: "number" },
        active: { type: "boolean" },
      },
      required: ["label", "count", "active"],
    });
  });

  it("converts object with strict + optional", () => {
    const schema = z.object({ x: z.number().optional(), y: z.string() }).strict();
    const json = toJSONSchema(schema);
    expect(json).toEqual({
      type: "object",
      properties: { x: { type: "number" }, y: { type: "string" } },
      required: ["y"],
      additionalProperties: false,
    });
  });

  it("converts enum", () => {
    const json = toJSONSchema(z.enum(["a", "b"]));
    expect(json).toEqual({ enum: ["a", "b"] });
  });

  it("converts nativeEnum", () => {
    const json = toJSONSchema(z.nativeEnum({ A: "a", B: "b" }));
    expect(json).toEqual({ enum: ["a", "b"] });
  });

  it("converts union", () => {
    const json = toJSONSchema(z.union([z.string(), z.number()]));
    expect(json).toEqual({ oneOf: [{ type: "string" }, { type: "number" }] });
  });

  it("converts array", () => {
    const json = toJSONSchema(z.array(z.string()));
    expect(json).toEqual({ type: "array", items: { type: "string" } });
  });

  it("converts tuple", () => {
    const json = toJSONSchema(z.tuple([z.string(), z.number()]));
    expect(json).toEqual({
      type: "array",
      items: [{ type: "string" }, { type: "number" }],
      additionalItems: false,
    });
  });

  it("123", () => {
    const people = z.tuple([z.string(), z.number(),z.object({point: z.number()})]);
    people.parse(["name", 20, {point: 20}]);
  });

  it("converts record", () => {
    const json = toJSONSchema(z.record(z.string(), z.boolean()));
    expect(json).toEqual({ type: "object", additionalProperties: { type: "boolean" } });
  });

  it("converts any", () => {
    expect(toJSONSchema(z.any())).toEqual({});
  });

  it("converts unknown", () => {
    expect(toJSONSchema(z.unknown())).toEqual({});
  });

  it("converts never", () => {
    expect(toJSONSchema(z.never())).toEqual({ not: {} });
  });

  it("converts literal", () => {
    expect(toJSONSchema(z.literal("admin"))).toEqual({ const: "admin" });
    expect(toJSONSchema(z.literal(42))).toEqual({ const: 42 });
  });

  it("converts date", () => {
    expect(toJSONSchema(z.date())).toEqual({ type: "string", format: "date-time" });
  });

  it("converts lazy", () => {
    const inner = z.string();
    const json = toJSONSchema(z.lazy(() => inner));
    expect(json).toEqual({ type: "string" });
  });

  it("converts number.int().positive()", () => {
    const json = toJSONSchema(z.number().int().positive());
    expect(json).toEqual({ type: "integer", exclusiveMinimum: 0 });
  });

  it("converts number.min().max()", () => {
    const json = toJSONSchema(z.number().min(1).max(12));
    expect(json).toEqual({ type: "number", minimum: 1, maximum: 12 });
  });

  it("converts number.int().min(1).max(12)", () => {
    const json = toJSONSchema(z.number().int().min(1).max(12));
    expect(json).toEqual({ type: "integer", minimum: 1, maximum: 12 });
  });

  it("converts string.regex()", () => {
    const json = toJSONSchema(z.string().regex("^\\d+$"));
    expect(json).toEqual({ type: "string", pattern: "^\\d+$" });
  });

  it("converts string.url()", () => {
    const json = toJSONSchema(z.string().url());
    expect(json).toEqual({ type: "string", format: "uri" });
  });

  it("converts string.email()", () => {
    const json = toJSONSchema(z.string().email());
    expect(json).toEqual({ type: "string", format: "email" });
  });

  it("converts nullable in JSON Schema", () => {
    const json = toJSONSchema(z.string().nullable());
    expect(json).toEqual({ oneOf: [{ type: "string" }, { type: "null" }] });
  });

  it("converts default in JSON Schema", () => {
    const json = toJSONSchema(z.string().default("hi"));
    expect(json).toEqual({ type: "string", default: "hi" });
  });

  it("converts nested object (library-style)", () => {
    const ButtonSchema = z.object({
      label: z.string(),
      variant: z.enum(["primary", "secondary"]).optional(),
    });
    const CardSchema = z.object({
      children: z.array(z.any()),
    });
    const combined = z.object({ Button: ButtonSchema, Card: CardSchema });
    const json = z.toJSONSchema(combined);
    expect(json.type).toBe("object");
    expect(json.properties).toHaveProperty("Button");
    expect(json.properties).toHaveProperty("Card");
    expect(json.required).toEqual(["Button", "Card"]);
    const buttonJson = json.properties.Button as Record<string, unknown>;
    expect(buttonJson.required).toEqual(["label"]);
  });

  it("default field inside object gets default in JSON", () => {
    const schema = z.object({
      tab: z.string().default("Dashboard"),
      size: z.object({ w: z.number().int().min(1).max(12) }).default({ w: 6 }),
    }).strict();
    const json = toJSONSchema(schema);
    expect(json.additionalProperties).toBe(false);
    expect(json.properties.tab).toEqual({ type: "string", default: "Dashboard" });
    expect(json.properties.size).toEqual({
      type: "object",
      properties: { w: { type: "integer", minimum: 1, maximum: 12 } },
      required: ["w"],
      default: { w: 6 },
    });
    expect(json.required).toBeUndefined();
  });

  it("merge result converts to JSON Schema correctly", () => {
    const a = z.object({ direction: z.enum(["row", "column"]) });
    const b = z.object({ children: z.array(z.any()) });
    const merged = a.merge(b);
    const json = toJSONSchema(merged);
    expect(json).toEqual({
      type: "object",
      properties: {
        direction: { enum: ["row", "column"] },
        children: { type: "array", items: {} },
      },
      required: ["direction", "children"],
    });
  });
});

describe("z.infer type-level API", () => {
  it("z.infer extracts string type", () => {
    type Result = Infer<StringSchema>;
    const check: Result = "hello";
    expect(typeof check).toBe("string");
  });

  it("z.infer extracts number type", () => {
    type Result = Infer<NumberSchema>;
    const check: Result = 42;
    expect(typeof check).toBe("number");
  });

  it("z.infer extracts boolean type", () => {
    type Result = Infer<BooleanSchema>;
    const check: Result = true;
    expect(typeof check).toBe("boolean");
  });

  it("z.infer extracts optional type", () => {
    type Result = Infer<OptionalSchema>;
    const check: Result = undefined;
    expect(check).toBeUndefined();
  });

  it("z.infer extracts nullable type", () => {
    type Result = Infer<NullableSchema>;
    const check1: Result = null;
    const check2: Result = "hello";
    expect(check1).toBeNull();
    expect(check2).toBe("hello");
  });

  it("z.infer extracts default type (inner type)", () => {
    type Result = Infer<DefaultSchema>;
    const check: Result = "hello";
    expect(check).toBe("hello");
  });
});

describe("ZodType / ZodObject / ZodTypeAny type aliases", () => {
  it("ZodType is compatible with Schema", () => {
    const schema: ZodType<string> = z.string() as ZodType<string>;
    expect(schema._def.type).toBe("string");
  });

  it("ZodObject is compatible with ObjectSchema", () => {
    const schema: ZodObject = z.object({ x: z.number() });
    expect(schema._def.type).toBe("object");
  });

  it("ZodTypeAny is compatible with any Schema", () => {
    const schema: ZodTypeAny = z.string();
    expect(schema._def.type).toBe("string");
  });
});

describe("SchemaDef type discrimination", () => {
  it("all _def.type values are correct", () => {
    expect(z.object({})._def.type).toBe("object");
    expect(z.string()._def.type).toBe("string");
    expect(z.number()._def.type).toBe("number");
    expect(z.boolean()._def.type).toBe("boolean");
    expect(z.enum(["a"])._def.type).toBe("enum");
    expect(z.union([])._def.type).toBe("union");
    expect(z.array(z.any())._def.type).toBe("array");
    expect(z.record(z.string(), z.any())._def.type).toBe("record");
    expect(z.any()._def.type).toBe("any");
    expect(z.unknown()._def.type).toBe("unknown");
    expect(z.never()._def.type).toBe("never");
    expect(z.literal("x")._def.type).toBe("literal");
    expect(z.tuple([])._def.type).toBe("tuple");
    expect(z.date()._def.type).toBe("date");
    expect(z.nativeEnum({ A: "a" })._def.type).toBe("nativeEnum");
    expect(z.lazy(() => z.string())._def.type).toBe("lazy");
    expect(z.string().optional()._def.type).toBe("optional");
    expect(z.string().default("x")._def.type).toBe("default");
    expect(z.string().nullable()._def.type).toBe("nullable");
  });
});

describe("WrapperDef unwrap pattern", () => {
  it("optional innerType accessible", () => {
    const schema = z.string().optional();
    expect(schema._def.innerType._def.type).toBe("string");
  });

  it("default innerType accessible", () => {
    const schema = z.string().default("hi");
    expect(schema._def.innerType._def.type).toBe("string");
  });

  it("nullable innerType accessible", () => {
    const schema = z.string().nullable();
    expect(schema._def.innerType._def.type).toBe("string");
  });

  it("chained wrappers: optional().default() innerType", () => {
    const schema = z.string().optional().default("fallback");
    expect(schema._def.type).toBe("default");
    expect(schema._def.innerType._def.type).toBe("optional");
    expect(schema._def.innerType._def.innerType._def.type).toBe("string");
  });
});

describe("Real-world usage patterns", () => {
  it("CanvasCard schema pattern", () => {
    const CanvasCardSchema = z.object({
      tab: z.string().default("Dashboard"),
      size: z.object({ w: z.number().int().min(1).max(12) }).default({ w: 6 }),
    }).strict();
    const json = toJSONSchema(CanvasCardSchema);
    expect(json.type).toBe("object");
    expect(json.additionalProperties).toBe(false);
    expect(json.properties.tab).toHaveProperty("default", "Dashboard");
    expect(json.properties.size).toHaveProperty("default");
  });

  it("Form schema pattern with default array", () => {
    const FormSchema = z.object({
      name: z.string(),
      fields: z.array(z.any()).default([]),
    });
    const json = toJSONSchema(FormSchema);
    expect(json.required).toEqual(["name"]);
    expect(json.properties.fields).toEqual({
      type: "array",
      items: {},
      default: [],
    });
  });

  it("Stack/Card merge pattern", () => {
    const FlexPropsSchema = z.object({
      direction: z.enum(["row", "column"]).optional(),
      gap: z.enum(["none", "xs", "s", "m", "l", "xl", "2xl"]).optional(),
    });
    const CardSchema = z.object({
      children: z.array(z.any()),
      variant: z.enum(["clear", "card", "sunk"]).optional(),
    }).merge(FlexPropsSchema);
    expect(CardSchema.shape).toHaveProperty("direction");
    expect(CardSchema.shape).toHaveProperty("gap");
    expect(CardSchema.shape).toHaveProperty("children");
    expect(CardSchema.shape).toHaveProperty("variant");
  });

  it("Union spread pattern for component library", () => {
    const baseUnion = z.union([z.string(), z.number()]);
    const extendedUnion = z.union([...baseUnion.options, z.boolean(), z.array(z.any())]);
    expect(extendedUnion.options.length).toBe(4);
    const json = toJSONSchema(extendedUnion);
    expect(json.oneOf.length).toBe(4);
  });

  it("DatePicker unknown pattern", () => {
    const DatePickerSchema = z.object({
      name: z.string(),
      mode: z.enum(["single", "range"]),
      value: z.unknown().optional(),
    });
    const json = toJSONSchema(DatePickerSchema);
    expect(json.required).toEqual(["name", "mode"]);
    expect(json.properties.value).toEqual({});
  });

  it("reactive pattern: record with boolean values", () => {
    const SwitchGroupSchema = z.object({
      name: z.string(),
      value: z.record(z.string(), z.boolean()).optional(),
    });
    const json = toJSONSchema(SwitchGroupSchema);
    expect(json.required).toEqual(["name"]);
    expect(json.properties.value).toEqual({
      type: "object",
      additionalProperties: { type: "boolean" },
    });
  });
});

describe("safeParse validation", () => {
  it("validates string schema", () => {
    expect(z.string().safeParse("hi").success).toBe(true);
    expect(z.string().safeParse(42).success).toBe(false);
  });

  it("validates number schema with constraints", () => {
    expect(z.number().safeParse(5).success).toBe(true);
    expect(z.number().safeParse("5").success).toBe(false);
    expect(z.number().int().safeParse(3).success).toBe(true);
    expect(z.number().int().safeParse(3.5).success).toBe(false);
    expect(z.number().min(1).max(12).safeParse(6).success).toBe(true);
    expect(z.number().min(1).max(12).safeParse(0).success).toBe(false);
    expect(z.number().min(1).max(12).safeParse(13).success).toBe(false);
    expect(z.number().positive().safeParse(-1).success).toBe(false);
  });

  it("validates boolean schema", () => {
    expect(z.boolean().safeParse(true).success).toBe(true);
    expect(z.boolean().safeParse("true").success).toBe(false);
  });

  it("validates enum schema", () => {
    expect(z.enum(["a", "b"]).safeParse("a").success).toBe(true);
    expect(z.enum(["a", "b"]).safeParse("c").success).toBe(false);
  });

  it("validates object schema with required fields", () => {
    const schema = z.object({ name: z.string(), age: z.number() });
    expect(schema.safeParse({ name: "Alice", age: 30 }).success).toBe(true);
    expect(schema.safeParse({ name: "Alice" }).success).toBe(false);
    expect(schema.safeParse({}).success).toBe(false);
  });

  it("validates strict object schema rejecting unknown props", () => {
    const schema = z.object({ name: z.string() }).strict();
    expect(schema.safeParse({ name: "Alice" }).success).toBe(true);
    expect(schema.safeParse({ name: "Alice", extra: "no" }).success).toBe(false);
  });

  it("validates optional fields", () => {
    const schema = z.object({ name: z.string(), nick: z.string().optional() });
    expect(schema.safeParse({ name: "A" }).success).toBe(true);
    expect(schema.safeParse({ name: "A", nick: "B" }).success).toBe(true);
  });

  it("validates and applies defaults", () => {
    const schema = z.object({ name: z.string(), tab: z.string().default("Dashboard") });
    const result = schema.safeParse({ name: "test" });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.tab).toBe("Dashboard");
    }
  });

  it("validates array schema", () => {
    expect(z.array(z.string()).safeParse(["a", "b"]).success).toBe(true);
    expect(z.array(z.string()).safeParse([1]).success).toBe(false);
    expect(z.array(z.string()).safeParse("not array").success).toBe(false);
  });

  it("validates union schema", () => {
    expect(z.union([z.string(), z.number()]).safeParse("hi").success).toBe(true);
    expect(z.union([z.string(), z.number()]).safeParse(42).success).toBe(true);
    expect(z.union([z.string(), z.number()]).safeParse(true).success).toBe(false);
  });

  it("validates record schema", () => {
    expect(z.record(z.string(), z.boolean()).safeParse({ a: true }).success).toBe(true);
    expect(z.record(z.string(), z.boolean()).safeParse({ a: 1 }).success).toBe(false);
  });

  it("validates any/unknown/never", () => {
    expect(z.any().safeParse("anything").success).toBe(true);
    expect(z.unknown().safeParse(undefined).success).toBe(true);
    expect(z.never().safeParse("x").success).toBe(false);
  });

  it("validates nullable schema", () => {
    expect(z.string().nullable().safeParse(null).success).toBe(true);
    expect(z.string().nullable().safeParse("hi").success).toBe(true);
    expect(z.string().nullable().safeParse(5).success).toBe(false);
  });

  it("validates literal schema", () => {
    expect(z.literal("x").safeParse("x").success).toBe(true);
    expect(z.literal("x").safeParse("y").success).toBe(false);
  });

  it("parse() throws on failure", () => {
    expect(() => z.string().parse(42)).toThrow();
    expect(z.string().parse("hi")).toBe("hi");
  });
});

describe("InferObject mapped type inference", () => {
  it("z.object infers precise object type", () => {
    const schema = z.object({ name: z.string(), age: z.number() });
    type Actual = Infer<typeof schema>;
    type Expected = { name: string; age: number };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.object infers with optional field", () => {
    const schema = z.object({ name: z.string(), nick: z.string().optional() });
    type Actual = Infer<typeof schema>;
    type Expected = { name: string; nick: string | undefined };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.object infers with default field", () => {
    const schema = z.object({ name: z.string(), tab: z.string().default("Dashboard") });
    type Actual = Infer<typeof schema>;
    type Expected = { name: string; tab: string };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.object infers with nullable field", () => {
    const schema = z.object({ name: z.string(), note: z.string().nullable() });
    type Actual = Infer<typeof schema>;
    type Expected = { name: string; note: string | null };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.object infers with boolean field", () => {
    const schema = z.object({ active: z.boolean() });
    type Actual = Infer<typeof schema>;
    type Expected = { active: boolean };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.object infers with nested object", () => {
    const schema = z.object({ size: z.object({ w: z.number() }) });
    type Actual = Infer<typeof schema>;
    type Expected = { size: { w: number } };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("z.enum infers union type", () => {
    const schema = z.enum(["row", "column"]);
    type Actual = Infer<typeof schema>;
    type Expected = "row" | "column";
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });
});

describe("Precise Infer for union/array/record/literal/tuple", () => {
  it("z.union infers union type", () => {
    const schema = z.union([z.string(), z.number()]);
    type Actual = Infer<typeof schema>;
    type Expected = string | number;
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.union with three options", () => {
    const schema = z.union([z.string(), z.number(), z.boolean()]);
    type Actual = Infer<typeof schema>;
    type Expected = string | number | boolean;
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.array infers array type", () => {
    const schema = z.array(z.string());
    type Actual = Infer<typeof schema>;
    type Expected = string[];
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.record infers record type", () => {
    const schema = z.record(z.string(), z.boolean());
    type Actual = Infer<typeof schema>;
    type Expected = Record<string, boolean>;
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.literal infers string literal type", () => {
    const schema = z.literal("admin");
    type Actual = Infer<typeof schema>;
    type Expected = "admin";
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.literal infers number literal type", () => {
    const schema = z.literal(42);
    type Actual = Infer<typeof schema>;
    type Expected = 42;
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.tuple infers tuple type", () => {
    const schema = z.tuple([z.string(), z.number()]);
    type Actual = Infer<typeof schema>;
    type Expected = [string, number];
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("z.tuple infers nested object tuple type", () => {
    const schema = z.tuple([z.string(), z.number(), z.object({ point: z.number() })]);
    type Actual = Infer<typeof schema>;
    type Expected = [string, number, { point: number }];
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });
});

describe("Complex combination validation - deep nesting", () => {
  it("object with nested array of objects safeParse", () => {
    const schema = z.object({
      items: z.array(z.object({ id: z.number(), label: z.string() })),
    });
    expect(schema.safeParse({ items: [{ id: 1, label: "a" }] }).success).toBe(true);
    expect(schema.safeParse({ items: [{ id: 1, label: 2 }] }).success).toBe(false);
    expect(schema.safeParse({ items: [{ id: "x", label: "a" }] }).success).toBe(false);
    expect(schema.safeParse({ items: "not array" }).success).toBe(false);
  });

  it("array of unions safeParse", () => {
    const schema = z.array(z.union([z.string(), z.number()]));
    expect(schema.safeParse(["a", 1]).success).toBe(true);
    expect(schema.safeParse(["a", 1, true]).success).toBe(false);
    expect(schema.safeParse([true]).success).toBe(false);
  });

  it("union of objects safeParse", () => {
    const schema = z.union([
      z.object({ type: z.literal("text"), content: z.string() }),
      z.object({ type: z.literal("number"), value: z.number() }),
    ]);
    expect(schema.safeParse({ type: "text", content: "hello" }).success).toBe(true);
    expect(schema.safeParse({ type: "number", value: 42 }).success).toBe(true);
    expect(schema.safeParse({ type: "text", value: 42 }).success).toBe(false);
    expect(schema.safeParse({ type: "image" }).success).toBe(false);
  });

  it("object with record field safeParse", () => {
    const schema = z.object({
      name: z.string(),
      flags: z.record(z.string(), z.boolean()),
    });
    expect(schema.safeParse({ name: "x", flags: { a: true, b: false } }).success).toBe(true);
    expect(schema.safeParse({ name: "x", flags: { a: 1 } }).success).toBe(false);
    expect(schema.safeParse({ name: "x", flags: [] }).success).toBe(false);
  });

  it("tuple with mixed wrappers safeParse", () => {
    const schema = z.tuple([z.string(), z.number().optional(), z.boolean().nullable()]);
    expect(schema.safeParse(["a", undefined, null]).success).toBe(true);
    expect(schema.safeParse(["a", 5, true]).success).toBe(true);
    expect(schema.safeParse(["a", null, null]).success).toBe(false);
    expect(schema.safeParse(["a"]).success).toBe(false);
  });

  it("nested object with optional+default safeParse", () => {
    const schema = z.object({
      config: z.object({
        debug: z.boolean().default(false),
        port: z.number().int().min(1).max(65535).default(3000),
      }).optional(),
    });
    const r1 = schema.safeParse({});
    expect(r1.success).toBe(true);
    const r2 = schema.safeParse({ config: { debug: true, port: 8080 } });
    expect(r2.success).toBe(true);
    if (r2.success) expect(r2.data.config.port).toBe(8080);
    const r3 = schema.safeParse({ config: { debug: true, port: 0 } });
    expect(r3.success).toBe(false);
    const r4 = schema.safeParse({ config: { port: 3000 } });
    expect(r4.success).toBe(true);
    if (r4.success) expect(r4.data.config.debug).toBe(false);
  });
});

describe("Complex combination validation - wrapper chaining", () => {
  it("optional().default() chain safeParse", () => {
    const schema = z.object({
      name: z.string(),
      nick: z.string().optional().default("anonymous"),
    });
    const r1 = schema.safeParse({ name: "Alice" });
    expect(r1.success).toBe(true);
    if (r1.success) expect(r1.data.nick).toBe("anonymous");
    const r2 = schema.safeParse({ name: "Alice", nick: "Bob" });
    expect(r2.success).toBe(true);
    if (r2.success) expect(r2.data.nick).toBe("Bob");
    const r3 = schema.safeParse({ name: "Alice", nick: undefined });
    expect(r3.success).toBe(true);
    if (r3.success) expect(r3.data.nick).toBe("anonymous");
  });

  it("nullable().default() chain safeParse", () => {
    const schema = z.object({
      note: z.string().nullable().default("N/A"),
    });
    const r1 = schema.safeParse({});
    expect(r1.success).toBe(true);
    if (r1.success) expect(r1.data.note).toBe("N/A");
    const r2 = schema.safeParse({ note: null });
    expect(r2.success).toBe(true);
    if (r2.success) expect(r2.data.note).toBe(null);
    const r3 = schema.safeParse({ note: "custom" });
    expect(r3.success).toBe(true);
    if (r3.success) expect(r3.data.note).toBe("custom");
  });

  it("optional().nullable() chain safeParse", () => {
    const schema = z.object({ val: z.string().optional().nullable() });
    expect(schema.safeParse({}).success).toBe(true);
    expect(schema.safeParse({ val: undefined }).success).toBe(true);
    expect(schema.safeParse({ val: null }).success).toBe(true);
    expect(schema.safeParse({ val: "hi" }).success).toBe(true);
    expect(schema.safeParse({ val: 42 }).success).toBe(false);
  });

  it("array.default() safeParse fills default on missing", () => {
    const schema = z.object({ tags: z.array(z.string()).default([]) });
    const r = schema.safeParse({});
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.tags).toEqual([]);
  });

  it("object.default() safeParse fills default on missing", () => {
    const schema = z.object({ size: z.object({ w: z.number() }).default({ w: 6 }) });
    const r = schema.safeParse({});
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.size).toEqual({ w: 6 });
  });

  it("enum.optional() safeParse", () => {
    const schema = z.object({ mode: z.enum(["edit", "view"]).optional() });
    expect(schema.safeParse({}).success).toBe(true);
    expect(schema.safeParse({ mode: "edit" }).success).toBe(true);
    expect(schema.safeParse({ mode: "delete" }).success).toBe(false);
  });

  it("union.optional() safeParse", () => {
    const schema = z.object({ val: z.union([z.string(), z.number()]).optional() });
    expect(schema.safeParse({}).success).toBe(true);
    expect(schema.safeParse({ val: "hi" }).success).toBe(true);
    expect(schema.safeParse({ val: 42 }).success).toBe(true);
    expect(schema.safeParse({ val: true }).success).toBe(false);
  });
});

describe("Complex combination validation - merge+strict+optional", () => {
  it("strict+optional+default merged object safeParse", () => {
    const base = z.object({ id: z.number() }).strict();
    const ext = z.object({ label: z.string().default("untitled"), tags: z.array(z.any()).optional() });
    const merged = base.merge(ext);
    const r1 = merged.safeParse({ id: 1 });
    expect(r1.success).toBe(true);
    if (r1.success) expect(r1.data.label).toBe("untitled");
    const r2 = merged.safeParse({ id: 1, label: "test", tags: [] });
    expect(r2.success).toBe(true);
    const r3 = merged.safeParse({ id: 1, extra: "no" });
    expect(r3.success).toBe(false);
  });

  it("merge with both sides having defaults", () => {
    const a = z.object({ x: z.string().default("a") });
    const b = z.object({ y: z.number().default(0) });
    const merged = a.merge(b);
    const r = merged.safeParse({});
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.x).toBe("a");
      expect(r.data.y).toBe(0);
    }
  });

  it("merge overwrite preserves overwritten field default", () => {
    const a = z.object({ x: z.string().default("old") });
    const b = z.object({ x: z.number().default(42) });
    const merged = a.merge(b);
    const r = merged.safeParse({});
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.x).toBe(42);
  });

  it("non-strict object accepts extra properties", () => {
    const schema = z.object({ name: z.string() });
    const r = schema.safeParse({ name: "Alice", extra: 1 });
    expect(r.success).toBe(true);
  });

  it("strict object rejects extra properties after merge from non-strict", () => {
    const a = z.object({ x: z.number() }).strict();
    const b = z.object({ y: z.string() });
    const merged = a.merge(b);
    expect(merged.safeParse({ x: 1, y: "hi" }).success).toBe(true);
    expect(merged.safeParse({ x: 1, y: "hi", z: true }).success).toBe(false);
  });
});

describe("Complex combination validation - JSON Schema edge cases", () => {
  it("nested object with enum+union+array JSON Schema", () => {
    const schema = z.object({
      role: z.enum(["admin", "user"]),
      profile: z.union([z.string(), z.object({ bio: z.string() })]),
      tags: z.array(z.enum(["a", "b", "c"])),
    });
    const json = toJSONSchema(schema);
    expect(json.properties.role).toEqual({ enum: ["admin", "user"] });
    expect(json.properties.profile).toEqual({
      oneOf: [{ type: "string" }, { type: "object", properties: { bio: { type: "string" } }, required: ["bio"] }],
    });
    expect(json.properties.tags).toEqual({ type: "array", items: { enum: ["a", "b", "c"] } });
    expect(json.required).toEqual(["role", "profile", "tags"]);
  });

  it("object with record+default+nullable JSON Schema", () => {
    const schema = z.object({
      meta: z.record(z.string(), z.any()).default({}),
      note: z.string().nullable(),
    });
    const json = toJSONSchema(schema);
    expect(json.required).toBeUndefined();
    expect(json.properties.meta).toEqual({ type: "object", additionalProperties: {}, default: {} });
    expect(json.properties.note).toEqual({ oneOf: [{ type: "string" }, { type: "null" }] });
  });

  it("array of tuples JSON Schema", () => {
    const schema = z.array(z.tuple([z.string(), z.number()]));
    const json = toJSONSchema(schema);
    expect(json).toEqual({
      type: "array",
      items: { type: "array", items: [{ type: "string" }, { type: "number" }], additionalItems: false },
    });
  });

  it("union of enums JSON Schema", () => {
    const schema = z.union([z.enum(["a", "b"]), z.enum(["c", "d"])]);
    const json = toJSONSchema(schema);
    expect(json).toEqual({ oneOf: [{ enum: ["a", "b"] }, { enum: ["c", "d"] }] });
  });

  it("literal boolean JSON Schema", () => {
    expect(toJSONSchema(z.literal(true))).toEqual({ const: true });
    expect(toJSONSchema(z.literal(false))).toEqual({ const: false });
  });

  it("empty object JSON Schema", () => {
    const schema = z.object({});
    const json = toJSONSchema(schema);
    expect(json).toEqual({ type: "object", properties: {} });
  });

  it("object with only optional fields JSON Schema", () => {
    const schema = z.object({ a: z.string().optional(), b: z.number().optional() });
    const json = toJSONSchema(schema);
    expect(json.required).toBeUndefined();
  });

  it("lazy wrapping object JSON Schema", () => {
    const inner = z.object({ name: z.string() });
    const schema = z.lazy(() => inner);
    const json = toJSONSchema(schema);
    expect(json.type).toBe("object");
    expect(json.properties).toHaveProperty("name");
  });

  it("number with all constraints JSON Schema", () => {
    const schema = z.number().int().positive().min(1).max(100);
    const json = toJSONSchema(schema);
    expect(json).toEqual({ type: "integer", exclusiveMinimum: 0, minimum: 1, maximum: 100 });
  });

  it("string with both pattern and format JSON Schema", () => {
    const schema = z.string().url().regex("^https://");
    const json = toJSONSchema(schema);
    expect(json.type).toBe("string");
    expect(json.format).toBe("uri");
    expect(json.pattern).toBe("^https://");
  });
});

describe("Complex combination validation - Record+Tuple+Literal+NativeEnum+Date", () => {
  it("record with object values safeParse", () => {
    const schema = z.record(z.string(), z.object({ v: z.number() }));
    expect(schema.safeParse({ a: { v: 1 }, b: { v: 2 } }).success).toBe(true);
    expect(schema.safeParse({ a: { v: "x" } }).success).toBe(false);
    expect(schema.safeParse([]).success).toBe(false);
  });

  it("record with union values safeParse", () => {
    const schema = z.record(z.string(), z.union([z.string(), z.number()]));
    expect(schema.safeParse({ a: "hi", b: 42 }).success).toBe(true);
    expect(schema.safeParse({ a: true }).success).toBe(false);
  });

  it("record with nullable values safeParse", () => {
    const schema = z.record(z.string(), z.string().nullable());
    expect(schema.safeParse({ a: "hi", b: null }).success).toBe(true);
    expect(schema.safeParse({ a: 42 }).success).toBe(false);
  });

  it("tuple with three different types safeParse", () => {
    const schema = z.tuple([z.string(), z.number(), z.boolean()]);
    expect(schema.safeParse(["a", 1, true]).success).toBe(true);
    expect(schema.safeParse(["a", 1]).success).toBe(false);
    expect(schema.safeParse(["a", 1, true, "extra"]).success).toBe(false);
    expect(schema.safeParse([1, "a", true]).success).toBe(false);
  });

  it("literal with boolean value safeParse", () => {
    expect(z.literal(true).safeParse(true).success).toBe(true);
    expect(z.literal(true).safeParse(false).success).toBe(false);
    expect(z.literal(false).safeParse(false).success).toBe(true);
  });

  it("literal with zero value safeParse", () => {
    expect(z.literal(0).safeParse(0).success).toBe(true);
    expect(z.literal(0).safeParse(1).success).toBe(false);
  });

  it("nativeEnum with mixed string+number values safeParse", () => {
    const schema = z.nativeEnum({ A: "alpha", B: 2 });
    expect(schema.safeParse("alpha").success).toBe(true);
    expect(schema.safeParse(2).success).toBe(true);
    expect(schema.safeParse("gamma").success).toBe(false);
    expect(schema.safeParse(99).success).toBe(false);
  });

  it("date safeParse with valid and invalid", () => {
    expect(z.date().safeParse(new Date()).success).toBe(true);
    expect(z.date().safeParse("2024-01-01").success).toBe(false);
    expect(z.date().safeParse(12345).success).toBe(false);
    expect(z.date().safeParse(null).success).toBe(false);
  });

  it("nativeEnum JSON Schema output", () => {
    enum Role { Admin = "admin", User = "user" }
    const json = toJSONSchema(z.nativeEnum(Role));
    expect(json).toEqual({ enum: ["admin", "user"] });
  });

  it("record with array values JSON Schema", () => {
    const json = toJSONSchema(z.record(z.string(), z.array(z.number())));
    expect(json).toEqual({ type: "object", additionalProperties: { type: "array", items: { type: "number" } } });
  });
});

describe("Complex combination validation - real-world patterns", () => {
  it("Table component schema safeParse", () => {
    const ColSchema = z.object({
      name: z.string(),
      label: z.string().optional(),
      type: z.enum(["text", "number", "date", "link"]).default("text"),
    });
    const TableSchema = z.object({
      data: z.array(z.record(z.string(), z.any())),
      columns: z.array(ColSchema),
    }).strict();
    expect(TableSchema.safeParse({
      data: [{ name: "Alice" }],
      columns: [{ name: "name" }],
    }).success).toBe(true);
    expect(TableSchema.safeParse({
      data: [{ name: "Alice" }],
      columns: [{ name: "name", type: "text" }],
    }).success).toBe(true);
    expect(TableSchema.safeParse({
      data: [{ name: "Alice" }],
      columns: [{ name: "name", type: "invalid" }],
    }).success).toBe(false);
    expect(TableSchema.safeParse({
      data: [{ name: "Alice" }],
      columns: [{ name: "name" }],
      extra: "no",
    }).success).toBe(false);
  });

  it("Table component schema JSON Schema", () => {
    const ColSchema = z.object({
      name: z.string(),
      label: z.string().optional(),
      type: z.enum(["text", "number", "date", "link"]).default("text"),
    });
    const TableSchema = z.object({
      data: z.array(z.record(z.string(), z.any())),
      columns: z.array(ColSchema),
    });
    const json = toJSONSchema(TableSchema);
    expect(json.required).toEqual(["data", "columns"]);
    expect(json.properties.columns.items.required).toEqual(["name"]);
  });

  it("Form with nested defaults safeParse", () => {
    const FormSchema = z.object({
      title: z.string(),
      fields: z.array(z.object({
        name: z.string(),
        required: z.boolean().default(false),
        placeholder: z.string().optional(),
      })).default([]),
    });
    const r1 = FormSchema.safeParse({ title: "My Form" });
    expect(r1.success).toBe(true);
    if (r1.success) {
      expect(r1.data.fields).toEqual([]);
    }
    const r2 = FormSchema.safeParse({ title: "My Form", fields: [{ name: "email" }] });
    expect(r2.success).toBe(true);
    if (r2.success) {
      expect(r2.data.fields[0].required).toBe(false);
    }
  });

  it("DashboardCard with size constraints safeParse", () => {
    const CardSchema = z.object({
      tab: z.string().default("Dashboard"),
      size: z.object({ w: z.number().int().min(1).max(12) }).default({ w: 6 }),
      content: z.any().optional(),
    }).strict();
    const r1 = CardSchema.safeParse({});
    expect(r1.success).toBe(true);
    if (r1.success) {
      expect(r1.data.tab).toBe("Dashboard");
      expect(r1.data.size).toEqual({ w: 6 });
    }
    const r2 = CardSchema.safeParse({ tab: "Charts", size: { w: 12 }, extra: true });
    expect(r2.success).toBe(false);
    const r3 = CardSchema.safeParse({ size: { w: 0 } });
    expect(r3.success).toBe(false);
    const r4 = CardSchema.safeParse({ size: { w: 13 } });
    expect(r4.success).toBe(false);
  });

  it("SwitchGroup with record validation", () => {
    const SwitchGroupSchema = z.object({
      name: z.string(),
      value: z.record(z.string(), z.boolean()).optional(),
    });
    const r1 = SwitchGroupSchema.safeParse({ name: "options" });
    expect(r1.success).toBe(true);
    const r2 = SwitchGroupSchema.safeParse({ name: "options", value: { a: true, b: false } });
    expect(r2.success).toBe(true);
    const r3 = SwitchGroupSchema.safeParse({ name: "options", value: { a: 1 } });
    expect(r3.success).toBe(false);
  });

  it("Component catalog: union of prop schemas safeParse", () => {
    const ButtonProps = z.object({ label: z.string(), variant: z.enum(["primary", "secondary"]).optional() });
    const InputProps = z.object({ value: z.string(), disabled: z.boolean().default(false) });
    const catalog = z.union([ButtonProps, InputProps]);
    expect(catalog.safeParse({ label: "Click" }).success).toBe(true);
    expect(catalog.safeParse({ value: "hello" }).success).toBe(true);
    expect(catalog.safeParse({ value: "hello", disabled: false }).success).toBe(true);
  });

  it("Lazy recursive tree safeParse", () => {
    const TreeNode = z.lazy(() =>
      z.object({
        label: z.string(),
        children: z.array(TreeNode).default([]),
      })
    );
    expect(TreeNode.safeParse({ label: "root" }).success).toBe(true);
    expect(TreeNode.safeParse({ label: "root", children: [] }).success).toBe(true);
    expect(TreeNode.safeParse({ label: "root", children: [{ label: "child" }] }).success).toBe(true);
    expect(TreeNode.safeParse({ label: "root", children: [{ label: "child", children: [{ label: "leaf" }] }] }).success).toBe(true);
    expect(TreeNode.safeParse({ children: [] }).success).toBe(false);
  });

  it("Lazy recursive tree safeParse JSON Schema uses inner type (non-recursive)", () => {
    const LazyString = z.lazy(() => z.string());
    const json = toJSONSchema(LazyString);
    expect(json).toEqual({ type: "string" });
  });

  it("Complex component with merge+enum+optional+default+strict", () => {
    const FlexProps = z.object({
      direction: z.enum(["row", "column"]).default("row"),
      gap: z.enum(["none", "xs", "s", "m", "l", "xl"]).optional(),
    });
    const CardBase = z.object({
      children: z.array(z.any()),
      variant: z.enum(["clear", "card", "sunk"]).default("card"),
    }).strict();
    const CardSchema = CardBase.merge(FlexProps);
    const r = CardSchema.safeParse({ children: [] });
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.variant).toBe("card");
      expect(r.data.direction).toBe("row");
    }
    expect(CardSchema.safeParse({ children: [], extra: 1 }).success).toBe(false);
  });
});

describe("Complex combination validation - parse() throw behavior", () => {
  it("parse() on nested object throws with details", () => {
    const schema = z.object({ inner: z.object({ x: z.number() }) });
    expect(() => schema.parse({ inner: { x: "not number" } })).toThrow();
  });

  it("parse() on strict object throws on extra prop", () => {
    const schema = z.object({ name: z.string() }).strict();
    expect(() => schema.parse({ name: "A", extra: 1 })).toThrow();
  });

  it("parse() on array throws on wrong element type", () => {
    const schema = z.array(z.number());
    expect(() => schema.parse([1, "two"])).toThrow();
  });

  it("parse() on tuple throws on wrong length", () => {
    const schema = z.tuple([z.string(), z.number()]);
    expect(() => schema.parse(["a"])).toThrow();
    expect(() => schema.parse(["a", 1, "extra"])).toThrow();
  });

  it("parse() on record throws on invalid value", () => {
    const schema = z.record(z.string(), z.number());
    expect(() => schema.parse({ a: "not number" })).toThrow();
  });

  it("parse() on union throws when no option matches", () => {
    const schema = z.union([z.string(), z.number()]);
    expect(() => schema.parse(true)).toThrow();
  });

  it("parse() on enum throws on invalid value", () => {
    expect(() => z.enum(["a", "b"]).parse("c")).toThrow();
  });

  it("parse() on literal throws on mismatch", () => {
    expect(() => z.literal("x").parse("y")).toThrow();
    expect(() => z.literal(42).parse(43)).toThrow();
  });

  it("parse() on date throws on non-Date", () => {
    expect(() => z.date().parse("2024-01-01")).toThrow();
  });

  it("parse() on number constraints throws", () => {
    expect(() => z.number().int().parse(3.5)).toThrow();
    expect(() => z.number().positive().parse(-1)).toThrow();
    expect(() => z.number().min(5).parse(4)).toThrow();
    expect(() => z.number().max(10).parse(11)).toThrow();
  });

  it("parse() on string constraints throws", () => {
    expect(() => z.string().regex("^\\d+$").parse("abc")).toThrow();
    expect(() => z.string().url().parse("noturl")).toThrow();
    expect(() => z.string().email().parse("noemail")).toThrow();
  });
});

describe("Complex combination validation - GlobalRegistry edge cases", () => {
  it("multiple schemas registered to same registry", () => {
    const registry = new GlobalRegistry();
    const schemaA = z.object({ a: z.string() });
    const schemaB = z.object({ b: z.number() });
    schemaA.register(registry, { id: "A" });
    schemaB.register(registry, { id: "B" });
    expect(registry.get(schemaA)).toEqual({ id: "A" });
    expect(registry.get(schemaB)).toEqual({ id: "B" });
    expect(registry.has(schemaA)).toBe(true);
    expect(registry.has(schemaB)).toBe(true);
  });

  it("unregistered schema returns undefined", () => {
    const registry = new GlobalRegistry();
    const schema = z.object({ x: z.string() });
    expect(registry.get(schema)).toBeUndefined();
    expect(registry.has(schema)).toBe(false);
  });

  it("globalRegistry is shared across calls", () => {
    const s1 = z.object({ a: z.string() });
    const s2 = z.object({ b: z.number() });
    s1.register(z.globalRegistry, { id: "S1" });
    s2.register(z.globalRegistry, { id: "S2" });
    expect(z.globalRegistry.get(s1)).toEqual({ id: "S1" });
    expect(z.globalRegistry.get(s2)).toEqual({ id: "S2" });
  });

  it("nested object schemas registered independently", () => {
    const registry = new GlobalRegistry();
    const inner = z.object({ x: z.number() });
    const outer = z.object({ inner, label: z.string() });
    inner.register(registry, { id: "Inner" });
    outer.register(registry, { id: "Outer" });
    expect(registry.get(inner)).toEqual({ id: "Inner" });
    expect(registry.get(outer)).toEqual({ id: "Outer" });
  });
});

describe("Complex combination validation - Infer with nested combos", () => {
  it("Infer array of objects", () => {
    const schema = z.array(z.object({ id: z.number(), name: z.string() }));
    type Actual = Infer<typeof schema>;
    type Expected = { id: number; name: string }[];
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("Infer object with array field", () => {
    const schema = z.object({ items: z.array(z.string()) });
    type Actual = Infer<typeof schema>;
    type Expected = { items: string[] };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("Infer object with union field", () => {
    const schema = z.object({ val: z.union([z.string(), z.number()]) });
    type Actual = Infer<typeof schema>;
    type Expected = { val: string | number };
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("Infer object with record field", () => {
    const schema = z.object({ flags: z.record(z.string(), z.boolean()) });
    type Actual = Infer<typeof schema>;
    type Expected = { flags: Record<string, boolean> };
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("Infer nested object with optional+default", () => {
    const schema = z.object({
      config: z.object({
        debug: z.boolean().default(false),
        port: z.number().optional(),
      }).optional(),
    });
    type Actual = Infer<typeof schema>;
    type Expected = { config: { debug: boolean; port: number | undefined } | undefined };
    const _check: Expected = {} as Actual;
    expect(true).toBe(true);
  });

  it("Infer tuple with enum", () => {
    const schema = z.tuple([z.string(), z.enum(["a", "b"])]);
    type Actual = Infer<typeof schema>;
    type Expected = [string, "a" | "b"];
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });

  it("Infer union with objects", () => {
    const schema = z.union([
      z.object({ type: z.literal("text"), content: z.string() }),
      z.object({ type: z.literal("image"), url: z.string() }),
    ]);
    type Actual = Infer<typeof schema>;
    type Expected = { type: "text"; content: string } | { type: "image"; url: string };
    const _notAny: IsAny<Actual> extends true ? never : Actual = {} as Actual;
    const _check: Expected = {} as _notAny;
    expect(true).toBe(true);
  });
});

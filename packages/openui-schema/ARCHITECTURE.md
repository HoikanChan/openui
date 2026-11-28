# OpenUI Schema — 架构设计文档

> 状态：设计阶段  
> 目标：移除 `lang-core`、`react-lang`、`react-ui`、`react-ui-dsl` 对外部 zod 的依赖，用轻量自研 schema 替换  
> 包名：`@openuidev/openui-schema`

---

## 1. 当前实现状态评估

### 1.1 已完整覆盖的能力

| API | 所在文件 | 说明 |
|---|---|---|
| `z.object(shape)` | `object.ts` | shape getter + strict 开关 |
| `z.string()` | `primitives.ts` | 基本字符串 schema |
| `z.number()` | `primitives.ts` | 支持 int/positive 链式调用 |
| `z.boolean()` | `primitives.ts` | 基本布尔 schema |
| `z.enum(values)` | `enum.ts` | 枚举约束 |
| `z.union(options)` | `union.ts` | `.options` getter，支持展开合并模式 |
| `z.array(element)` | `array.ts` | 数组 schema |
| `z.record(key, value)` | `record.ts` | 键值映射 schema |
| `z.any()` | `any.ts` | 无约束透传 |
| `.optional()` | `core.ts` | OptionalSchema，_def.innerType 可反查 |
| `.strict()` | `object.ts` | 对象不允许额外字段，_def.strict 标记 |
| `.int()` | `primitives.ts` | 数值整数约束 |
| `.positive()` | `primitives.ts` | 数值 > 0 约束 |
| `.register(registry, {id})` | `core.ts` | 委托给 Registry 接口 |
| `z.globalRegistry` | `registry.ts` | WeakMap 单例 |
| `z.toJSONSchema(schema)` | `json-schema.ts` | 递归转 JSON Schema draft-07 |
| `Schema._def` 公开属性 | `core.ts` | 替代 zod 的 `_zod.def`，可直接 introspection |
| `ObjectSchema.shape` getter | `object.ts` | evaluator/introspection 通过此属性查找字段 schema |
| `UnionSchema.options` getter | `union.ts` | 支持 `[...existingUnion.options, newRef]` 展开合并 |

### 1.2 缺口 — 需新增运行时实现

#### 当前项目已有真实使用的 API（必须实现）

| API | 使用位置 | 说明 |
|---|---|---|
| `.merge(otherObjectSchema)` | Stack/schema.ts, Card/schema.ts | 合并两个 object shape |
| `.default(value)` | Form/schema.ts, CanvasCard/schema.ts, dataSchemas.ts | 带默认值的 schema 包装，`_def.type = "default"` |
| `z.unknown()` | DatePicker/schema.ts, HTMLLoader/schema.ts | `_def.type = "unknown"` |
| `.min(n)` / `.max(n)` | CanvasCard/schema.ts | 数值范围约束，JSON Schema minimum/maximum |

#### 扩展目标 API（暂未使用，但设计目标要求实现）

| API | 说明 |
|---|---|
| `.nullable()` | `_def.type = "nullable"`，输出类型 `T | null` |
| `z.literal(value)` | `_def.type = "literal"`，精确值匹配 |
| `z.tuple([...])` | `_def.type = "tuple"`，定长有序列表 |
| `z.date()` | `_def.type = "date"`，日期类型 |
| `z.never()` | `_def.type = "never"`，不可匹配类型 |
| `z.nativeEnum(obj)` | `_def.type = "nativeEnum"`，原生枚举映射 |
| `z.lazy(fn)` | `_def.type = "lazy"`，延迟求值（解决循环引用） |
| `.regex(pattern)` | 字符串正则约束，JSON Schema `pattern` |
| `.url()` | 字符串 URL 约束，JSON Schema `format: "uri"` |
| `.email()` | 字符串邮箱约束，JSON Schema `format: "email"` |

### 1.3 缺口 — 仅类型层（无运行时行为）

| 类型 API | 使用范围 | 频率 | 说明 |
|---|---|---|---|
| `z.infer<typeof X>` | 全项目所有包 | **~68处** | 从 `Schema<SchemaDef, T>` 提取输出类型 T |
| `z.ZodType<T>` | Table/schema.ts, library.ts | 中 | 泛型 schema 类型标注 |
| `z.ZodObject<any>` | library.ts, react-lang/library.ts | 中 | Object schema 类型约束 |
| `z.ZodTypeAny` | SwitchGroup, Select, CheckBoxGroup | 低 | `{ ref: z.ZodTypeAny }` RefComponent 模式 |
| `import type { z }` | Charts/view-utils.ts | 1处 | 仅引入类型级 z namespace |

### 1.4 introspection 兼容性缺口

`lang-core/src/library.ts` 通过 zod v4 的私有路径读取内部结构：

```ts
function getZodDef(schema: unknown): any {
  return (schema as any)?._zod?.def;  // ← zod v4 私有路径
}
```

当前 `openui-schema` 使用 `_def`（公开平坦路径）。迁移需要将 introspection 代码重写为 `_def` 直接访问，消除 `as any` 侵入式窥探。

---

## 2. 整体架构概览

### 2.1 分层图

```
┌──────────────────────────────────────────────────────────────┐
│                      消费方包                                 │
│  lang-core · react-lang · react-ui · react-ui-dsl           │
│                                                             │
│  import { z } from "@openuidev/openui-schema"               │
│  z.object · z.string · z.infer · z.ZodType · z.toJSONSchema │
└──────────────────────────────────┬───────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────┐
│                     openui-schema                            │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  core.ts │  │ object.ts│  │primitives│  │  enum.ts │    │
│  │ Schema   │  │ObjectSch │  │String/Num│  │EnumSchema│    │
│  │ Optional │  │ merge()  │  │ Bool/int │  │          │    │
│  │ Registry │  │ strict() │  │ pos/min  │  │          │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ union.ts │  │ array.ts │  │record.ts │  │  any.ts  │    │
│  │ options  │  │ element  │  │key/val   │  │          │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │unknown.ts│  │never.ts  │  │ literal  │  │ tuple.ts │    │
│  │ date.ts  │  │ nativeE  │  │ lazy.ts  │  │DefaultSch│    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│                                                             │
│  ┌───────────────┐  ┌─────────────────────────────────────┐ │
│  │ registry.ts   │  │        json-schema.ts              │ │
│  │ GlobalRegistry│  │ toJSONSchema — 递归转换              │ │
│  │ WeakMap<object│  │ object/string/number/boolean/       │ │
│  │ register/get  │  │ enum/union/array/record/any/        │ │
│  │ /has          │  │ unknown/optional/default/           │ │
│  └───────────────┘  │ nullable/literal/tuple/date/        │ │
│                     │ min/max/regex/url/email/strict       │ │
│                     └─────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    z.ts                               │   │
│  │  工厂命名空间 + globalRegistry + toJSONSchema        │   │
│  │  + 类型级导出 (ZodType, ZodObject, infer)            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                   index.ts                            │   │
│  │  公开 API 表面 — 所有 class/type/function 导出       │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心设计原则：`Schema<TDef, T>` 双泛型参数

每个 schema 类继承自 `Schema<TDef extends SchemaDef, T>`：

- **`TDef`** — 运行时元数据形状。控制 `_def` 的结构、introspection 行为和 JSON Schema 输出。
- **`T`** — 编译期输出类型。承载推导出的 TypeScript 类型，为 `z.infer<S>` 提供管线。

当前各子类的 T 状态：

```
StringSchema  → Schema<StringDef, string>      ✅ T 已精确
NumberSchema  → Schema<NumberDef, number>      ✅ T 已精确
BooleanSchema → Schema<BooleanDef, boolean>    ✅ T 已精确
EnumSchema    → Schema<EnumDef, any>           ⚠️ T 应为 values 的联合类型
ObjectSchema  → Schema<ObjectDef, any>         ⚠️ T 应为 shape 的映射类型
OptionalSchema → Schema<OptionalDef, T|undef>  ✅ T 从 innerType 传播
```

`T` 参数是 **`z.infer` 的管线**。后续添加 infer 时：

1. ObjectSchema：通过映射类型计算 T → `{ [K in keyof Shape]: InferField<Shape[K]> }`
2. EnumSchema：通过联合类型计算 T → `Values[number]`
3. 所有包装 schema（Optional、Default、Nullable）：通过 innerType 传播 T

---

## 3. 各类 API 的详细设计

### 3.1 构造器 API — 新增 schema 类型

#### `z.unknown()`

```
UnknownDef: { type: "unknown" }
UnknownSchema extends Schema<UnknownDef, unknown>

JSON Schema: {}（空对象 — 无约束，与 any 一致）
introspection: resolveTypeAnnotation → "unknown"
```

在本项目语境下运行时行为与 `z.any()` 完全一致（不做验证）。区别仅在 TypeScript 语义层面 — `unknown` 需要类型收窄后才能使用，`any` 不需要。

#### `z.never()`

```
NeverDef: { type: "never" }
NeverSchema extends Schema<NeverDef, never>

JSON Schema: { not: {} }（匹配空集）
introspection: resolveTypeAnnotation → "never"
```

#### `z.literal(value)`

```
LiteralDef: { type: "literal", values: [value] }
LiteralSchema<T> extends Schema<LiteralDef, T>

z.literal("admin") → Schema<LiteralDef, "admin">
z.literal(42)      → Schema<LiteralDef, 42>

JSON Schema: { const: value }
introspection: resolveTypeAnnotation → `"admin"` / `42`
```

与 `library.ts:240-245` 已有的 `type === "literal"` + `def.values` introspection 完全匹配。

#### `z.tuple(schemas)`

```
TupleDef: { type: "tuple", items: Schema[] }
TupleSchema<T> extends Schema<TupleDef, T>

z.tuple([z.string(), z.number()]) → Schema<TupleDef, [string, number]>

JSON Schema: { type: "array", items: [json1, json2], additionalItems: false }
introspection: resolveTypeAnnotation → `[string, number]`
```

#### `z.date()`

```
DateDef: { type: "date" }
DateSchema extends Schema<DateDef, Date>

JSON Schema: { type: "string", format: "date-time" }
introspection: resolveTypeAnnotation → "date"
```

#### `z.nativeEnum(enumObject)`

```
NativeEnumDef: { type: "nativeEnum", values: string[] | number[] }
NativeEnumSchema<T> extends Schema<NativeEnumDef, T>

z.nativeEnum({ A: "a", B: "b" }) → values = ["a", "b"]
z.nativeEnum({ A: 1, B: 2 })     → values = [1, 2]

JSON Schema: { enum: values }
introspection: resolveTypeAnnotation → `"a" | "b"`
```

从对象的自身字符串键条目中提取值（匹配 zod 的字符串枚举行为），数值枚举也支持。

#### `z.lazy(fn)`

```
LazyDef: { type: "lazy", getter: () => Schema }
LazySchema<T> extends Schema<LazyDef, T>

z.lazy(() => recursiveSchema)

JSON Schema: 延迟 — 调用 getter() 后再转换内部 schema
introspection: 调用 getter() 解开
```

Lazy 解决循环引用问题。getter 在首次访问时调用，内部 schema 被缓存。

---

### 3.2 链式 API — 新增方法

#### `.default(value)` — 适用于任意 Schema

**语义**：为 schema 字段附带默认值。在 object 字段级分析中，`.default()` 字段**不加入 required 列表**（与 `.optional()` 同等处理）。

```
DefaultDef: { type: "default", innerType: Schema, defaultValue: unknown }
DefaultSchema<T> extends Schema<DefaultDef, T>

z.string().default("hello")          → 输出类型 string
z.array(X).default([])               → 输出类型 X[]
z.object({w: z.number()}).default({w:6}) → 输出类型 {w: number}

JSON Schema: 将 default 值合并到内部类型的 JSON Schema 上
  { ...innerJson, default: value }

introspection: unwrap() 跳过外层（与 optional 同等处理）
```

#### `.nullable()` — 适用于任意 Schema

**语义**：字段值可以是 null。在 object 字段级分析中，`.nullable()` 字段**不加入 required 列表**。

```
NullableDef: { type: "nullable", innerType: Schema }
NullableSchema<T> extends Schema<NullableDef, T | null>

z.string().nullable() → 输出类型 string | null

JSON Schema: { oneOf: [innerJson, { type: "null" }] }
introspection: unwrap() 一同跳过（与 optional/default 同列）
```

#### `.min(n)` / `.max(n)` — 适用于 NumberSchema

**语义**：记录数值的最小/最大约束。

```
NumberDef 新增字段:
  min?: number
  max?: number

z.number().int().min(1).max(12)

JSON Schema: { type: "integer", minimum: 1, maximum: 12 }
```

链式方法返回新 `NumberSchema` 实例（与 `.int()` 和 `.positive()` 同模式）。

**与 `.positive()` 的交互**：`.positive()` 输出 `exclusiveMinimum: 0`。`.min(0)` 输出 `minimum: 0`。两者同时存在时 `exclusiveMinimum` 优先（positive 代表 > 0）。

#### `.regex(pattern)` — 适用于 StringSchema

**语义**：记录字符串的正则约束。

```
StringDef 新增字段:
  pattern?: string

z.string().regex(/^\d+$/)

JSON Schema: { type: "string", pattern: "^\\d+$" }
```

StringSchema 需升级为可变 def 类（与 NumberSchema 同模式）：

```ts
export class StringSchema<T = string> extends Schema<StringDef, T> {
  constructor(def: StringDef = { type: "string" }) { super(def); }
  regex(pattern: string | RegExp): StringSchema<T> {
    const p = typeof pattern === "string" ? pattern : pattern.source;
    return new StringSchema({ ...this._def, pattern: p });
  }
}
```

#### `.url()` / `.email()` — 适用于 StringSchema

**语义**：记录字符串的格式约束（URL 或邮箱）。

```
StringDef 新增字段:
  format?: "uri" | "email"

z.string().url()   → _def.format = "uri"
z.string().email() → _def.format = "email"

JSON Schema:
  url   → { type: "string", format: "uri" }
  email → { type: "string", format: "email" }
```

链式模式与 `.regex()` 一致，在新实例上设置 `_def.format`。

#### `.merge(other)` — 适用于 ObjectSchema

**语义**：将另一个 ObjectSchema 的 shape 合并到当前 shape。other 的字段覆盖 self 中同名的字段。

```
ObjectSchema(shapeA).merge(ObjectSchema(shapeB))
  → ObjectSchema({ ...shapeA, ...shapeB })
```

实现：

```ts
merge(other: ObjectSchema): ObjectSchema {
  const mergedShape = { ...this._def.shape, ...other._def.shape };
  return new ObjectSchema(mergedShape, this._def.strict || other._def.strict);
}
```

---

### 3.3 introspection 兼容性 — `_def` 路径迁移

#### 当前 zod v4 路径（在 lang-core 中）：

```ts
function getZodDef(schema: unknown): any {
  return (schema as any)?._zod?.def;  // ← zod v4 私有路径，需要 as any 窥探
}
```

#### 目标 openui-schema 路径：

```ts
function getSchemaDef(schema: unknown): SchemaDef | undefined {
  if (schema instanceof Schema) return schema._def;
  return undefined;
}
```

**迁移收益**：
- 类型安全 — 返回 `SchemaDef` 而非 `any`
- 不再需要 `as any` 强制类型转换
- `instanceof Schema` 检查可靠（单一基类）
- 所有 `_def.type` 字符串检查保持不变：`"object"` / `"string"` / `"number"` / `"boolean"` / `"enum"` / `"union"` / `"array"` / `"record"` / `"any"` / `"optional"` / `"default"` / `"nullable"` / `"literal"` / `"tuple"` / `"date"` / `"never"` / `"nativeEnum"` / `"lazy"` / `"unknown"`

#### unwrap 函数重设计：

```ts
function unwrap(schema: Schema): Schema {
  let s = schema;
  while (s._def.type === "optional" || s._def.type === "default" || s._def.type === "nullable") {
    s = (s._def as OptionalDef | DefaultDef | NullableDef).innerType;
  }
  return s;
}
```

不再需要 `getZodDef()` 中间层 — 直接访问 `._def.type`。

---

### 3.4 类型级 API 设计 — `z.infer` 与兼容别名

#### 问题：`z` 是运行时 `const` 对象，但 `z.infer` 是类型

zod 的 `z` 命名空间混合了运行时函数和类型级工具：

```ts
import { z } from "zod";
z.string()           // 运行时调用
z.infer<typeof X>    // 类型级提取
z.ZodType<T>         // 类型级标注
```

在 TypeScript 中，`const` 对象无法承载 type-only 成员。解决方案：**双重导出 + 声明合并**。

#### 设计：`z` 运行时 + `z` 类型命名空间

```ts
// z.ts — 运行时命名空间
export const z = {
  object, string, number, boolean, enum, union, array, record,
  any, unknown, never, literal, tuple, date, nativeEnum, lazy,
  globalRegistry, toJSONSchema,
};

// types.ts — 类型命名空间（通过声明合并注入运行时 z）
export type ZodType<T = any> = Schema<SchemaDef, T>;
export type ZodObject<T = any> = ObjectSchema<T>;
export type ZodTypeAny = Schema<SchemaDef, any>;

// infer.ts — 工具类型
export type Infer<S extends Schema<any, any>> = S extends Schema<any, infer T> ? T : never;
```

#### 声明合并（在 index.ts 中）：

```ts
export { z } from "./z";

declare module "./z" {
  namespace z {
    type Infer<S extends Schema<any, any>> = Infer<S>;
    type ZodType<T = any> = ZodType<T>;
    type ZodObject<T = any> = ZodObject<T>;
    type ZodTypeAny = ZodTypeAny;
  }
}
```

这使得消费方可以用与 zod 完全一致的方式使用：

```ts
import { z } from "@openuidev/openui-schema";

// 运行时（当前已可用）
z.object({ name: z.string() })

// 类型级（声明合并后可用）
type Props = z.infer<typeof ButtonSchema>;
type Component = z.ZodObject<any>;
type Ref = z.ZodTypeAny;
```

#### `z.infer` 实现策略

`Infer<S>` 工具类型从 `Schema<SchemaDef, T>` 中提取 `T`。各类型的推导状态：

```
Infer<StringSchema>       → string          ✅ T = string 已设置
Infer<NumberSchema>       → number          ✅ T = number 已设置
Infer<BooleanSchema>      → boolean         ✅ T = boolean 已设置
Infer<EnumSchema<["a","b"]>> → "a"|"b"     ⚠️ 需要 T = Values[number]
Infer<ObjectSchema<{name: StringSchema}>>
  → { name: string }                       ⚠️ 需要映射类型计算
Infer<OptionalSchema<StringSchema>>
  → string | undefined                      ✅ T 从 innerType 传播
Infer<DefaultSchema<StringSchema>>
  → string                                  ⚠️ 需要 DefaultSchema<T>
Infer<NullableSchema<StringSchema>>
  → string | null                           ⚠️ 需要 NullableSchema<T>
```

**阶段 1**（立即）：`Infer<S>` = 直接提取 T 参数。对基本类型和包装 schema 有效。ObjectSchema/EnumSchema 暂为 `T = any`。

**阶段 2**（跟进）：为 ObjectSchema 和 EnumSchema 添加映射类型计算：

```ts
type InferObject<Shape extends Record<string, Schema>> = {
  [K in keyof Shape]: InferField<Shape[K]>
};
type InferField<S extends Schema> =
  S extends OptionalSchema<infer T> ? T | undefined
  : S extends DefaultSchema<infer T> ? T
  : S extends NullableSchema<infer T> ? T | null
  : Infer<S>;
```

---

### 3.5 JSON Schema 输出 — 完整转换矩阵

| Schema 类型 | JSON Schema 输出 |
|---|---|
| `z.object({...})` | `{ type: "object", properties: {...}, required: [...] }` |
| `z.object({...}).strict()` | `{ type: "object", properties: {...}, required: [...], additionalProperties: false }` |
| `z.string()` | `{ type: "string" }` |
| `z.string().regex(/../)` | `{ type: "string", pattern: "..." }` |
| `z.string().url()` | `{ type: "string", format: "uri" }` |
| `z.string().email()` | `{ type: "string", format: "email" }` |
| `z.number()` | `{ type: "number" }` |
| `z.number().int()` | `{ type: "integer" }` |
| `z.number().positive()` | `{ type: "number", exclusiveMinimum: 0 }` |
| `z.number().int().positive()` | `{ type: "integer", exclusiveMinimum: 0 }` |
| `z.number().min(n).max(m)` | `{ type: "number", minimum: n, maximum: m }` |
| `z.number().int().min(1).max(12)` | `{ type: "integer", minimum: 1, maximum: 12 }` |
| `z.boolean()` | `{ type: "boolean" }` |
| `z.enum([...])` | `{ enum: [...] }` |
| `z.nativeEnum({...})` | `{ enum: [...] }` |
| `z.union([...])` | `{ oneOf: [...] }` |
| `z.array(X)` | `{ type: "array", items: {...} }` |
| `z.tuple([...])` | `{ type: "array", items: [...], additionalItems: false }` |
| `z.record(K, V)` | `{ type: "object", additionalProperties: {...} }` |
| `z.any()` | `{}` |
| `z.unknown()` | `{}` |
| `z.never()` | `{ not: {} }` |
| `z.literal(val)` | `{ const: val }` |
| `z.date()` | `{ type: "string", format: "date-time" }` |
| `z.lazy(fn)` | 延迟到内部 schema 的输出 |
| `.optional()` | （在 object 级处理：字段不进 required 列表） |
| `.default(val)` | `{ ...innerJson, default: val }` |
| `.nullable()` | `{ oneOf: [innerJson, { type: "null" }] }` |

---

### 3.6 `SchemaDef` 类型体系 — 完整判别映射

```
SchemaDef                    { type: string }                ← 基类，开放扩展
  ├── ObjectDef              { type: "object", shape, strict? }
  ├── StringDef              { type: "string", pattern?, format? }
  ├── NumberDef              { type: "number", int?, positive?, min?, max? }
  ├── BooleanDef             { type: "boolean" }
  ├── EnumDef                { type: "enum", values }
  ├── NativeEnumDef          { type: "nativeEnum", values }
  ├── UnionDef               { type: "union", options }
  ├── ArrayDef               { type: "array", element }
  ├── TupleDef               { type: "tuple", items }
  ├── RecordDef              { type: "record", keyType, valueType }
  ├── AnyDef                 { type: "any" }
  ├── UnknownDef             { type: "unknown" }
  ├── NeverDef               { type: "never" }
  ├── LiteralDef             { type: "literal", values }
  ├── DateDef                { type: "date" }
  ├── LazyDef                { type: "lazy", getter }
  ├── OptionalDef            { type: "optional", innerType }
  ├── DefaultDef             { type: "default", innerType, defaultValue }
  └── NullableDef            { type: "nullable", innerType }
```

所有包装类型（Optional、Default、Nullable）遵循相同的结构模式：`{ type, innerType }`。这种一致性意味着 lang-core 的 `unwrap()` 函数可以用一个 `while` 循环穿越所有三层。

---

## 4. 文件组织 — 更新后的结构

```
packages/openui-schema/src/
  core.ts            ← Schema<TDef,T>, SchemaDef, WrapperDef, OptionalSchema, DefaultSchema, NullableSchema, Registry
  object.ts          ← ObjectSchema (shape, strict, merge)
  primitives.ts      ← StringSchema (regex, url, email), NumberSchema (int, positive, min, max), BooleanSchema
  enum.ts            ← EnumSchema
  native-enum.ts     ← NativeEnumSchema
  union.ts           ← UnionSchema (options)
  array.ts           ← ArraySchema
  tuple.ts           ← TupleSchema
  record.ts          ← RecordSchema
  any.ts             ← AnySchema
  unknown.ts         ← UnknownSchema
  never.ts           ← NeverSchema
  literal.ts         ← LiteralSchema
  date.ts            ← DateSchema
  lazy.ts            ← LazySchema
  registry.ts        ← GlobalRegistry (WeakMap)
  json-schema.ts     ← toJSONSchema (递归转换，完整矩阵)
  types.ts           ← ZodType, ZodObject, ZodTypeAny 类型别名
  infer.ts           ← Infer<S> 工具类型
  z.ts               ← z 命名空间（运行时工厂 + globalRegistry + toJSONSchema + 声明合并注入类型）
  index.ts           ← 公开 API 表面（所有导出）
  __tests__/         ← Vitest 测试套件
```

**边界原则**：每个 schema 类型是自包含文件。新增一个类型只需要：
1. 创建新 `.ts` 文件
2. 在 `json-schema.ts` 加 1 个 `case`
3. 在 `z.ts` 加 1 个属性
4. 在 `index.ts` 加 2 行导出

对现有文件零修改。

---

## 5. 包装 Schema 架构 — Optional / Default / Nullable 三件套

三个包装 schema 共享相同的结构模式，设计为统一家族：

```ts
// core.ts — 共享包装模式

interface WrapperDef extends SchemaDef {
  innerType: Schema;
}

// OptionalDef extends WrapperDef { type: "optional" }
// DefaultDef extends WrapperDef { type: "default"; defaultValue: unknown }
// NullableDef extends WrapperDef { type: "nullable" }
```

**在 object 字段级分析中**，三个包装的行为一致：
- 该字段**不加入 required 列表**
- 内部类型决定字段的 JSON Schema

```ts
// json-schema.ts — object 字段处理逻辑
for (const [key, fieldSchema] of Object.entries(def.shape)) {
  const wrapperType = fieldSchema._def.type;
  if (wrapperType === "optional" || wrapperType === "default" || wrapperType === "nullable") {
    const inner = (fieldSchema._def as WrapperDef).innerType;
    const fieldJson = convert(inner);
    if (wrapperType === "default") {
      fieldJson.default = (fieldSchema as DefaultSchema)._def.defaultValue;
    }
    if (wrapperType === "nullable") {
      properties[key] = { oneOf: [fieldJson, { type: "null" }] };
    } else {
      properties[key] = fieldJson;
    }
  } else {
    properties[key] = convert(fieldSchema);
    required.push(key);
  }
}
```

---

## 6. 各包迁移策略

### 6.1 lang-core

| 变更项 | 类型 | 工作量 |
|---|---|---|
| `import { z } from "zod"` → `import { z } from "@openuidev/openui-schema"` | 导入替换 | 低 |
| `getZodDef()` → `instanceof Schema` 检查 + `_def` 直接访问 | introspection 重写 | 中 |
| `z.ZodObject<any>` → `z.ZodObject`（类型别名） | 类型级 | 低 |
| `z.ZodType<...>` → `z.ZodType<...>`（类型别名） | 类型级 | 低 |
| `z.infer<T>` → `z.infer<T>`（工具类型） | 类型级 | 低 |
| package.json 移除 zod peerDependency | 配置 | 低 |

**关键文件**：`src/library.ts` — 重写 introspection 工具函数（getZodDef → getSchemaDef，unwrap → 使用 _def 直接访问）。其余部分（createLibrary、defineComponent、buildComponentSpecs、toJSONSchema）只使用高层 z API，均已覆盖。

### 6.2 react-lang

| 变更项 | 类型 | 工作量 |
|---|---|---|
| `import { z } from "zod"` → `import { z } from "@openuidev/openui-schema"` | 导入替换 | 低 |
| `reactive()` 类型签名：`z.ZodType` → `z.ZodType`（同名别名） | 类型级 | 低 |
| `z.infer<T>` → `z.infer<T>`（同名工具类型） | 类型级 | 低 |
| package.json 移除 zod peerDependency | 配置 | 低 |

**关键文件**：`src/runtime/reactive.ts` — 仅在类型注解中使用 `z.ZodType` 和 `z.infer<T>`。运行时行为是 `markReactive()` + 身份 cast，不涉及 zod 内部。

### 6.3 react-ui

| 变更项 | 类型 | 工作量 |
|---|---|---|
| ~60 个 schema.ts：导入替换 | 导入替换 | 中（机械批量） |
| `.merge()` 使用（Stack、Card） | 新 API | 低 |
| `.default()` 使用（Form、dataSchemas） | 新 API | 低 |
| `z.unknown()`（DatePicker） | 新 API | 低 |
| `z.ZodTypeAny` RefComponent 模式 | 类型级 | 低 |
| `z.infer<typeof X>` 类型导出 | 类型级 | 中（~20处） |
| package.json 移除 zod peerDependency | 配置 | 低 |

### 6.4 react-ui-dsl

| 变更项 | 类型 | 工作量 |
|---|---|---|
| ~29 个 schema.ts + index.tsx：导入替换 | 导入替换 | 中（机械批量） |
| `.strict()` 使用（Table、Descriptions、Card、CanvasCard） | ✅ 已覆盖 | — |
| `.default()` 使用（CanvasCard） | 新 API | 低 |
| `.min/.max` 使用（CanvasCard） | 新 API | 低 |
| `z.unknown()`（HTMLLoader） | 新 API | 低 |
| `z.ZodType<ColProps>` 标注（Table） | 类型级 | 低 |
| `z.infer<typeof X>` 类型导出 | 类型级 | 中（~15处） |
| `import type { z }`（Charts/view-utils.ts） | 类型级 | 低 |
| package.json 移除 zod peerDependency | 配置 | 低 |

---

## 7. 明确不实现的边界

以下 zod API **不实现**且**将来也不实现**：

| API | 原因 |
|---|---|
| `safeParse()` / `parse()` | 本项目从不使用 zod 做数据验证。schema 对象仅是元数据。 |
| `refine()` / `superRefine()` | 自定义验证逻辑 — 项目中未使用。 |
| `transform()` | 数据变换管道 — 未使用。需运行时求值引擎。 |
| `preprocess()` | 输入预处理 — 未使用。 |
| `z.promise()` | 异步验证 — 未使用。 |
| `z.function()` | 函数 schema — 未使用。 |
| `z.map()` / `z.set()` | 集合 schema — 未使用。 |
| `z.intersection()` | 交叉类型 — 未使用。 |
| `z.discriminatedUnion()` | 可辨识联合 — 未使用（所有联合都是 `z.union`）。 |
| `.brand()` / `.readonly()` | 类型品牌化 — 未使用。 |

**设计理由**：openui-schema 服务于明确角色 — **组件契约定义 + introspection + JSON Schema 导出**，为 OpenUI 生成式 UI 管线提供支撑。它不是通用验证库。每个实现的 API 都因项目当前使用或近期需要而存在。

---

## 8. 实施优先级与分期

### 阶段 1 — 关键路径（使 lang-core 迁移可行）

| 事项 | 前置依赖 |
|---|---|
| `.merge()` 在 ObjectSchema 上 | ObjectSchema |
| `.default()` 在 Schema 上（DefaultSchema） | core.ts |
| `.nullable()` 在 Schema 上（NullableSchema） | core.ts |
| `z.unknown()`（UnknownSchema） | any.ts 模式 |
| `.min()/.max()` 在 NumberSchema 上 | NumberSchema |
| `z.infer<S>` 工具类型 阶段 1（提取 T） | Schema<TDef,T> |
| `z.ZodType` / `z.ZodObject` / `z.ZodTypeAny` 类型别名 | Schema, ObjectSchema |
| lang-core introspection 重写（getSchemaDef + unwrap） | 所有 schema |
| `z` 命名空间声明合并 | types.ts + infer.ts |

### 阶段 2 — 扩展（使 react-ui/react-ui-dsl 全面覆盖）

| 事项 | 前置依赖 |
|---|---|
| `z.literal()` | SchemaDef 模式 |
| `z.tuple()` | SchemaDef 模式 |
| `z.date()` | SchemaDef 模式 |
| `z.nativeEnum()` | SchemaDef 模式 |
| `z.never()` | SchemaDef 模式 |
| `z.lazy()` | SchemaDef 模式 |
| `.regex()` 在 StringSchema 上 | StringDef |
| `.url()` / `.email()` 在 StringSchema 上 | StringDef |
| JSON Schema 完整转换矩阵更新 | 所有新增类型 |
| `z.infer` 阶段 2（ObjectSchema 映射类型、EnumSchema 联合类型） | 阶段 1 infer |

### 阶段 3 — 完善

| 事项 | 前置依赖 |
|---|---|
| ObjectSchema `.partial()` / `.pick()` / `.omit()` | ObjectSchema |
| 可辨识联合支持 | UnionSchema |
| `.describe()` 文档字符串 | SchemaDef |
| 与 zod 的性能基准对比 | 全阶段 |

# OpenUI Schema 当前实现架构梳理

> 梳理时间：2026-06-30  
> 版本：0.2.0  
> 测试状态：184 项全通过

---

## 1. 文件清单与职责

| 文件 | 行数 | 核心职责 |
|---|---|---|
| `core.ts` | 93 | 基础设施层 — Schema 基类（含 safeParse/parse）、三件套包装（Optional/Default/Nullable）、WrapperDef/Registry 接口 |
| `object.ts` | 27 | ObjectSchema — shape getter、strict()、merge() |
| `primitives.ts` | 66 | StringSchema（regex/url/email）、NumberSchema（int/positive/min/max）、BooleanSchema |
| `enum.ts` | 12 | EnumSchema<T extends string> — 字符串枚举联合推导 |
| `union.ts` | 16 | UnionSchema — options getter + 展开合并 |
| `array.ts` | 12 | ArraySchema — 元素 schema |
| `record.ts` | 13 | RecordSchema — keyType/valueType |
| `any.ts` | 11 | AnySchema — 无约束 |
| `unknown.ts` | 11 | UnknownSchema — unknown 类型 |
| `never.ts` | 11 | NeverSchema — 不可匹配类型 |
| `literal.ts` | 12 | LiteralSchema — 精确值匹配 |
| `tuple.ts` | 12 | TupleSchema — 定长有序列表 |
| `date.ts` | 11 | DateSchema — 日期类型 |
| `native-enum.ts` | 15 | NativeEnumSchema — 原生枚举映射 |
| `lazy.ts` | 21 | LazySchema — 延迟求值 + 缓存 |
| `registry.ts` | 17 | GlobalRegistry — WeakMap 实现 |
| `json-schema.ts` | 150 | toJSONSchema — 18 种 schema 的递归 JSON Schema 转换 |
| `types.ts` | 6 | 类型别名 — ZodType/ZodObject/ZodTypeAny |
| `infer.ts` | 7 | `Infer<S>` — 从 Schema 第二泛型参数提取 T；InferObject<Shape> — 映射类型精确推导 |
| `validate.ts` | 184 | 运行时验证引擎 — 全 18 种 schema 的 safeParse 递归验证 + validated data 传播 + deep wrapper required 检查 |
| `z.ts` | 41 | z 运行时值 — 18 个泛型工厂函数 + globalRegistry + toJSONSchema |
| `infer.ts` | 7 | `Infer<S>` + InferObject/InferTuple/InferUnion/IsAny — Phase 3 精确推导 |
| `any-schema-def.ts` | 36 | AnySchemaDef — 18 种 Def 判别联合（15 行独立 import + 19 行联合定义） |
| `index.ts` | 69 | 公开 API — 所有 class/type/function 导出 + namespace z 声明合并（namespace 内类型通过 import 别名 Infer_/ZodType_/ZodObject_/ZodTypeAny_ 间接引用） |
| `scripts/patch-dts.mjs` | 52 | 构建后修补 — .d.ts namespace z 类型注入 + z$1→z 重命名 + 健壮性检查（替换前后验证目标存在与内容变化） |

---

## 2. 类型体系总览

### 2.1 SchemaDef 判别联合 — 当前已实现的 18 种

```
SchemaDef              { type: string }               ← 基类
  ├── ObjectDef        { type: "object", shape, strict? }
  ├── StringDef        { type: "string", pattern?, format? }
  ├── NumberDef        { type: "number", int?, positive?, min?, max? }
  ├── BooleanDef       { type: "boolean" }
  ├── EnumDef          { type: "enum", values }
  ├── NativeEnumDef    { type: "nativeEnum", values }
  ├── UnionDef         { type: "union", options }
  ├── ArrayDef         { type: "array", element }
  ├── TupleDef         { type: "tuple", items }
  ├── RecordDef        { type: "record", keyType, valueType }
  ├── AnyDef           { type: "any" }
  ├── UnknownDef       { type: "unknown" }
  ├── NeverDef         { type: "never" }
  ├── LiteralDef       { type: "literal", values }
  ├── DateDef          { type: "date" }
  ├── LazyDef          { type: "lazy", getter }
  ├── OptionalDef      { type: "optional", innerType }  ← 继承 WrapperDef
  ├── DefaultDef       { type: "default", innerType, defaultValue }  ← 继承 WrapperDef
  └── NullableDef      { type: "nullable", innerType }  ← 继承 WrapperDef
```

### 2.2 AnySchemaDef 判别联合

```ts
type AnySchemaDef =
  | ObjectDef | StringDef | NumberDef | BooleanDef
  | EnumDef | UnionDef | ArrayDef | RecordDef
  | AnyDef | UnknownDef | NeverDef | LiteralDef
  | TupleDef | DateDef | NativeEnumDef | LazyDef
  | OptionalDef | DefaultDef | NullableDef;
```

18 种具体 Def 全覆盖，不含基类 SchemaDef（`type: string` 阻止窄化）。

---

## 3. 类继承体系

```
Schema<TDef extends SchemaDef, T = any>                  ← 基类（core.ts:31）
  │
  ├── OptionalSchema<T>    extends Schema<OptionalDef, T | undefined>
  ├── DefaultSchema<T>     extends Schema<DefaultDef, T>
  ├── NullableSchema<T>    extends Schema<NullableDef, T | null>
  │
  ├── ObjectSchema<T>      extends Schema<ObjectDef, T>                 ← T = InferObject<Shape> (Phase 2)
  ├── StringSchema<T>      extends Schema<StringDef, T>
  ├── NumberSchema<T>      extends Schema<NumberDef, T>
  ├── BooleanSchema<T>     extends Schema<BooleanDef, T>
  ├── EnumSchema<T>        extends Schema<EnumDef, T>                   ← T = Values[number] 联合类型
  ├── NativeEnumSchema<T>  extends Schema<NativeEnumDef, T>
  ├── UnionSchema<T>       extends Schema<UnionDef, T>
  ├── ArraySchema<T>       extends Schema<ArrayDef, T>
  ├── TupleSchema<T>       extends Schema<TupleDef, T>
  ├── RecordSchema<T>      extends Schema<RecordDef, T>
  ├── AnySchema<T>         extends Schema<AnyDef, T>
  ├── UnknownSchema<T>     extends Schema<UnknownDef, T>
  ├── NeverSchema          extends Schema<NeverDef, never>              ← T 精确为 never
  ├── LiteralSchema<T>     extends Schema<LiteralDef, T>
  ├── DateSchema           extends Schema<DateDef, Date>                ← T 精确为 Date
  ├── LazySchema<T>        extends Schema<LazyDef, T>
```

### 双泛型参数 T 的当前状态

| Schema 类 | T 值 | 精确度 |
|---|---|---|
| StringSchema | `string` | ✅ 精确 |
| NumberSchema | `number` | ✅ 精确 |
| BooleanSchema | `boolean` | ✅ 精确 |
| DateSchema | `Date` | ✅ 精确 |
| NeverSchema | `never` | ✅ 精确 |
| OptionalSchema | `T | undefined` | ✅ 从 innerType 传播 |
| DefaultSchema | `T` | ✅ 从 innerType 传播 |
| NullableSchema | `T | null` | ✅ 从 innerType 传播 |
| EnumSchema | `Values[number]` | ✅ **Phase 2 完成** — 联合推导 |
| ObjectSchema | `InferObject<Shape>` | ✅ **Phase 2 完成** — 映射类型推导 |
| 其余构造 schema | `any` | ⚠️ Phase 3 |

---

## 4. Schema 基类方法清单

```ts
class Schema<TDef, T> {
  readonly _def: TDef;

  optional(): OptionalSchema<T>;
  default(defaultValue: T): DefaultSchema<T>;
  nullable(): NullableSchema<T>;
  register(registry: Registry, meta: { id: string }): void;
  safeParse(input: unknown): SafeParseResult<T>;   // ← 运行时验证
  parse(input: unknown): T;                          // ← throw on failure
}
```

### safeParse / parse 实现说明

safeParse 和 parse 基于 `validate.ts` 中的递归验证引擎实现，覆盖全部 18 种 `_def.type`：

- **string**：类型检查 + pattern + format(uri/email)
- **number**：类型检查 + int + positive + min/max
- **boolean**：类型检查
- **object**：required 字段检查 + strict 模式拒绝未知属性 + 递归验证子字段 + default 值自动填充
- **enum**：值包含检查
- **union**：first-match 策略
- **array**：逐元素验证
- **record**：逐键值验证
- **any/unknown**：直接通过
- **never**：永远失败
- **literal**：精确值匹配
- **tuple**：长度 + 逐元素验证
- **date**：instanceof Date
- **nativeEnum**：值包含
- **lazy**：调用 getter 后验证
- **optional/default/nullable**：wrapper 解包验证

返回类型为 discriminated union：
```ts
type SafeParseResult<T> = { success: true; data: T } | { success: false; error: { message: string } };
```

---

## 5. z 命名空间运行时成员

```ts
const z = {
  object:     <Shape>(shape: Shape)  → ObjectSchema<InferObject<Shape>>,  // ← 泛型推导
  string:     <T>()                  → StringSchema<T>,
  number:     <T>()                  → NumberSchema<T>,
  boolean:    <T>()                  → BooleanSchema<T>,
  enum:       <T, Values>(values)    → EnumSchema<Values[number]>,        // ← 联合推导
  union:      (options)              → UnionSchema(options),
  array:      (element)              → ArraySchema(element),
  record:     (key, value)           → RecordSchema(key, value),
  any:        ()                     → AnySchema(),
  unknown:    ()                     → UnknownSchema(),
  never:      ()                     → NeverSchema(),
  literal:    <T>(value)             → LiteralSchema(value),
  tuple:      (items)                → TupleSchema(items),
  date:       ()                     → DateSchema(),
  nativeEnum: (enumObject)           → NativeEnumSchema(enumObject),
  lazy:       (getter)               → LazySchema(getter),
  globalRegistry:                    → GlobalRegistry 单例,
  toJSONSchema:                      → toJSONSchema 函数引用,
};
```

### z 命名空间类型级成员（通过构建后修补注入）

由于 tsdown 生成 `.d.ts` 时会剥离 namespace 声明合并的类型成员，需要通过 `scripts/patch-dts.mjs` 在构建后注入：

```ts
declare namespace z {
  type infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;
  type Infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;
  type ZodType<T = any>    = Schema<SchemaDef, T>;
  type ZodObject<T = any>  = ObjectSchema<T>;
  type ZodTypeAny          = Schema<SchemaDef, any>;
}
```

这使得消费方可以用与 zod 完全一致的语法：

```ts
import { z } from "@openuidev/openui-schema";
type Props = z.infer<typeof ButtonSchema>;   // ← 小写 infer（zod 规范）
type Props = z.Infer<typeof ButtonSchema>;   // ← 大写 Infer（兼容）
z.ZodType<string>                             // ← 类型级
z.ZodObject<any>                              // ← 类型级
z.ZodTypeAny                                   // ← 类型级
z.object({ name: z.string() })                // ← 运行时，推导为 ObjectSchema<{ name: string }>
z.enum(["row", "column"])                     // ← 运行时，推导为 EnumSchema<"row" | "column">
```

---

## 6. Infer<S> 和 InferObject<Shape> 工具类型

### Phase 1 — Infer<S>

```ts
type Infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;
```

从 `Schema<TDef, T>` 中提取第二个泛型参数 `T`。

### Phase 2 — InferObject<Shape>

```ts
type InferObject<Shape extends Record<string, Schema<SchemaDef, any>>> = {
  [K in keyof Shape]: Infer<Shape[K]>
};
```

将 ObjectSchema 的 shape 映射为精确的对象类型。每个字段通过 `Infer<Shape[K]>` 推导：
- `StringSchema` → `string`
- `OptionalSchema<string>` → `string | undefined`
- `DefaultSchema<string>` → `string`
- `NullableSchema<string>` → `string | null`
- 嵌套 `ObjectSchema` → 递归 InferObject

### Infer 推导效果（Phase 3 完成）

| 输入 | Infer 结果 | 说明 |
|---|---|---|
| `StringSchema` | `string` | ✅ 精确 |
| `NumberSchema` | `number` | ✅ 精确 |
| `BooleanSchema` | `boolean` | ✅ 精确 |
| `DateSchema` | `Date` | ✅ 精确 |
| `NeverSchema` | `never` | ✅ 精确 |
| `OptionalSchema<string>` | `string | undefined` | ✅ 精确 |
| `DefaultSchema<string>` | `string` | ✅ 精确 |
| `NullableSchema<string>` | `string | null` | ✅ 精确 |
| `EnumSchema<"row"|"column">` | `"row" | "column"` | ✅ **Phase 2** |
| `ObjectSchema<{ name: string }>` | `{ name: string }` | ✅ **Phase 2** |
| `UnionSchema<[String,Number]>` | `string | number` | ✅ **Phase 3** |
| `ArraySchema<String>` | `string[]` | ✅ **Phase 3** |
| `RecordSchema<String,Boolean>` | `Record<string, boolean>` | ✅ **Phase 3** |
| `LiteralSchema<"admin">` | `"admin"` | ✅ **Phase 3** |
| `TupleSchema<[String,Number]>` | `[string, number]` | ✅ **Phase 3** |

---

## 7. toJSONSchema 完整转换矩阵

| _def.type | JSON Schema 输出 | 处理函数 |
|---|---|---|
| `"object"` | `{ type: "object", properties, required?, additionalProperties?: false }` | `convertObject()` |
| `"string"` | `{ type: "string", pattern?, format? }` | `convertString()` |
| `"number"` | `{ type: "integer"|"number", exclusiveMinimum?, minimum?, maximum? }` | `convertNumber()` |
| `"boolean"` | `{ type: "boolean" }` | inline |
| `"enum"` | `{ enum: values }` | inline |
| `"nativeEnum"` | `{ enum: values }` | inline |
| `"union"` | `{ oneOf: [...] }` | inline |
| `"array"` | `{ type: "array", items: ... }` | inline |
| `"tuple"` | `{ type: "array", items: [...], additionalItems: false }` | inline |
| `"record"` | `{ type: "object", additionalProperties: ... }` | inline |
| `"any"` | `{}` | inline |
| `"unknown"` | `{}` | inline |
| `"never"` | `{ not: {} }` | inline |
| `"literal"` | `{ const: value }` | inline |
| `"date"` | `{ type: "string", format: "date-time" }` | inline |
| `"lazy"` | 延迟 → 调用 getter 后 convert | inline |
| `"optional"` | 递归 convert innerType | inline |
| `"default"` | `{ ...innerJson, default: value }` | `convertDefault()` |
| `"nullable"` | `{ oneOf: [innerJson, { type: "null" }] }` | inline |

---

## 8. GlobalRegistry 实现

```ts
class GlobalRegistry implements Registry {
  private store = new WeakMap<object, { id: string }>;

  register(schema: Schema, meta: { id: string }): void
  get(schema: Schema): { id: string } | undefined
  has(schema: Schema): boolean
}
```

---

## 9. 测试覆盖现状

测试文件 `schema.test.ts` 共 184 项测试，覆盖以下模块：

| 模块 | 测试项数 |
|---|---|
| z.object/string/number/boolean | 5+8 |
| z.enum/union/array/record/any/unknown/never/literal/tuple/date/nativeEnum/lazy | 16 |
| .optional/.default/.nullable/.register/globalRegistry | 12 |
| toJSONSchema | 23 |
| z.infer 类型级 | 6 |
| ZodType/ZodObject/ZodTypeAny | 3 |
| SchemaDef 判别 + WrapperDef unwrap | 5 |
| Real-world patterns | 5 |
| safeParse 验证 | 15+1 |
| **InferObject mapped type inference** | **7** ← Phase 2 新增 |
| **Precise Infer for union/array/record/literal/tuple** | **8** ← Phase 3 新增 |
| **Complex combination validation — deep nesting** | **6** ← 新增 |
| **Complex combination validation — wrapper chaining** | **7** ← 新增 |
| **Complex combination validation — merge+strict+optional** | **5** ← 新增 |
| **Complex combination validation — JSON Schema edge cases** | **10** ← 新增 |
| **Complex combination validation — Record/Tuple/Literal/NativeEnum/Date** | **10** ← 新增 |
| **Complex combination validation — real-world patterns** | **9** ← 新增 |
| **parse() throw behavior** | **11** ← 新增 |
| **GlobalRegistry edge cases** | **4** ← 新增 |
| **Infer nested combos** | **7** ← 新增 |

---

## 10. 对比设计目标的完成度

### 构造器 API — 16/16 ✅

| 目标 API | 状态 |
|---|---|
| z.object / z.string / z.number / z.boolean | ✅ |
| z.enum / z.union / z.array / z.record | ✅ |
| z.any / z.unknown / z.never / z.literal | ✅ |
| z.tuple / z.date / z.nativeEnum / z.lazy | ✅ |

### 链式 API — 13/13 ✅

| 目标 API | 状态 |
|---|---|
| .optional / .strict / .int / .positive / .register | ✅ |
| .default / .nullable / .min / .max | ✅ |
| .regex / .url / .email / .merge | ✅ |

### 类型级 API — 5/5 ✅

| 目标类型 API | 状态 |
|---|---|
| `z.infer<typeof X>` (小写) | ✅ namespace patch |
| `z.Infer<typeof X>` (大写) | ✅ namespace patch |
| `z.ZodType<T>` | ✅ |
| `z.ZodObject<T>` | ✅ |
| `z.ZodTypeAny` | ✅ |

### 全局 API — 2/2 ✅

| 目标 API | 状态 |
|---|---|
| `z.globalRegistry` | ✅ |
| `z.toJSONSchema` | ✅ |

### 运行时验证 — 2/2 ✅（已实现，原约束排除）

| API | 状态 |
|---|---|
| `safeParse()` | ✅ 已实现 |
| `parse()` | ✅ 已实现 |
| `refine()` | ❌ 不实现（保持） |
| `superRefine()` | ❌ 不实现（保持） |
| `transform()` | ❌ 不实现（保持） |

### Infer 推导精度

| Schema 类型 | Phase 1 | Phase 2 | Phase 3 | 状态 |
|---|---|---|---|---|
| StringSchema | ✅ | — | — | ✅ |
| NumberSchema | ✅ | — | — | ✅ |
| BooleanSchema | ✅ | — | — | ✅ |
| OptionalSchema | ✅ | — | — | ✅ |
| DefaultSchema | ✅ | — | — | ✅ |
| NullableSchema | ✅ | — | — | ✅ |
| EnumSchema | `any` | `Values[number]` | — | ✅ **Phase 2** |
| ObjectSchema | `any` | `InferObject<Shape>` | — | ✅ **Phase 2** |
| UnionSchema | `any` | — | 联合 Infer | ✅ **Phase 3** |
| ArraySchema | `any` | — | `Infer<Element>[]` | ✅ **Phase 3** |
| RecordSchema | `any` | — | `Record<string, Infer<Value>>` | ✅ **Phase 3** |
| LiteralSchema | `any` | — | 精确字面量 | ✅ **Phase 3** |
| TupleSchema | `any` | — | 定长 InferTuple | ✅ **Phase 3** |

---

## 11. 构建后修补机制 (patch-dts.mjs)

tsdown 生成 `.d.ts` 时将 namespace 声明合并的类型成员剥离为空壳，且将 `const z` 重命名为 `z$1` 以避免与 namespace `z` 冲突。`scripts/patch-dts.mjs` 执行两个修补操作：

1. **重命名**：`declare const z$1:` → `declare const z:` — 使 const 和 namespace 同名，TypeScript 才能正确合并值与类型声明
2. **注入**：`declare namespace z {}` → 完整的类型声明块 — 恢复被剥离的 Infer/infer/ZodType/ZodObject/ZodTypeAny

修补脚本包含健壮性检查：每次替换后验证内容确实发生变化，若替换未匹配则抛出错误终止构建。

---

## 12. 运行时验证引擎关键改进

### validated data 传播

原始 validate.ts 的 object/array/tuple/record 验证器只复制原始输入值，不使用内部验证后的 `r.data`。这导致嵌套 default 值无法在结果中传播（如 `{ config: { port: 3000 } }` 中 `debug` 的 `default(false)` 丢失）。

修复后所有验证器均使用 `r.data` 替代原始值：
- **object**：`result[key] = r.data` 传播子字段验证结果 + 外层 default 填充
- **array**：`arr[i] = r.data` 传播元素验证结果
- **tuple**：`result[i] = r.data` 传播元素验证结果
- **record**：`result[key] = vr.data` 传播值验证结果
- **union**：`return r` 替代 `return ok(input)` 传播匹配选项的完整验证结果

### deep wrapper required 检查

原始 requiredKeys 计算只检查 `_def.type === "optional" || "default"`，导致 `.optional().nullable()` 等深层包装链被误判为 required（外层是 nullable，非 optional）。

新增 `allowsUndefined(schema)` 函数：任何 wrapper 类型（optional/default/nullable）均视为 non-required。与 JSON Schema `isDeepOptional()` 保持一致。

### JSON Schema deep wrapper 处理

原始 `convertObject` 只检查一层 wrapper type。新增三个辅助函数：
- `isDeepOptional(schema)` — 判断字段是否 non-required
- `unwrapToLeaf(schema)` — 递归解包到叶 schema
- `collectWrapperMeta(schema, meta)` — 从 wrapper 链收集 hasDefault/defaultValue/hasNullable 信息

---

## 13. 当前架构的待完善项

| 项目 | 说明 | 优先级 |
|---|---|---|
| ObjectSchema .partial/.pick/.omit | 无下游使用，暂不实现 | 低 |
| .describe() | 文档字符串元数据，无下游使用 | 低 |
| 递归 lazy toJSONSchema | 会栈溢出，需加 depth limit 或 cycle detection | 低 |

---

## 14. Strictness prompt 功能

lang-core `PromptOptions`/`PromptSpec` 新增 `strictness` 字段（`"standard" | "strict"`）：
- **standard 模式**：排除 `STRICT:` 前缀标记的规则
- **strict 模式**：保留所有规则（含 `STRICT:` 前缀可见标记）

Strictness 类型通过 lang-core → react-lang 导出链传播。react-ui-dsl `dslLibrary` 中 3 条 data-honesty 规则加 `STRICT:` 前缀。

---

## 15. 迁移完成状态

| 包 | zod → openui-schema | 测试通过 |
|---|---|---|
| lang-core | ✅ 完成 | 93/93 |
| react-lang | ✅ 完成 | 15/15 |
| react-ui-dsl | ✅ 完成（68 文件替换） | 317/332（15 pre-existing 失败） |
| react-ui | ✅ 完成（63 文件替换） | 类型检查 + 构建通过 |
| openui-schema | ✅ 自身 | 184/184 |
| docs | ⏭️ 跳过（独立 Next.js 应用） | — |
| lang-core zod v4 dependency | ✅ 已移除 | — |

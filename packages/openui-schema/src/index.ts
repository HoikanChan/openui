export { z } from "./z";

export { Schema, OptionalSchema, DefaultSchema, NullableSchema } from "./core";
export type { SchemaDef, OptionalDef, DefaultDef, NullableDef, WrapperDef, Registry } from "./core";

export { ObjectSchema } from "./object";
export type { ObjectDef } from "./object";

export { StringSchema, NumberSchema, BooleanSchema } from "./primitives";
export type { StringDef, NumberDef, BooleanDef } from "./primitives";

export { EnumSchema } from "./enum";
export type { EnumDef } from "./enum";

export { UnionSchema } from "./union";
export type { UnionDef } from "./union";

export { ArraySchema } from "./array";
export type { ArrayDef } from "./array";

export { RecordSchema } from "./record";
export type { RecordDef } from "./record";

export { AnySchema } from "./any";
export type { AnyDef } from "./any";

export { UnknownSchema } from "./unknown";
export type { UnknownDef } from "./unknown";

export { NeverSchema } from "./never";
export type { NeverDef } from "./never";

export { LiteralSchema } from "./literal";
export type { LiteralDef } from "./literal";

export { TupleSchema } from "./tuple";
export type { TupleDef } from "./tuple";

export { DateSchema } from "./date";
export type { DateDef } from "./date";

export { NativeEnumSchema } from "./native-enum";
export type { NativeEnumDef } from "./native-enum";

export { LazySchema } from "./lazy";
export type { LazyDef } from "./lazy";

export { GlobalRegistry } from "./registry";

export { toJSONSchema } from "./json-schema";
export type { JSONSchema } from "./json-schema";

export type { ZodType, ZodObject, ZodTypeAny } from "./types";
export type { Infer, InferObject, InferTuple, InferUnion, IsAny } from "./infer";
export type { AnySchemaDef } from "./any-schema-def";
export type { SafeParseResult, SafeParseSuccess, SafeParseError } from "./validate";

import type { Schema, SchemaDef } from "./core";
import type { Infer as Infer_ } from "./infer";
import type { ZodType as ZodType_, ZodObject as ZodObject_, ZodTypeAny as ZodTypeAny_ } from "./types";

export namespace z {
  type infer<S extends Schema<SchemaDef, any>> = Infer_<S>;
  type Infer<S extends Schema<SchemaDef, any>> = Infer_<S>;
  type ZodType<T = any> = ZodType_<T>;
  type ZodObject<T = any> = ZodObject_<T>;
  type ZodTypeAny = ZodTypeAny_;
}

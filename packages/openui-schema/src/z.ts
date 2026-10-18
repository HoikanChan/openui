import type { Schema, SchemaDef } from "./core";
import type { Infer, InferObject, InferTuple, InferUnion } from "./infer";
import { ObjectSchema } from "./object";
import { StringSchema, NumberSchema, BooleanSchema } from "./primitives";
import { EnumSchema } from "./enum";
import { UnionSchema } from "./union";
import { ArraySchema } from "./array";
import { RecordSchema } from "./record";
import { AnySchema } from "./any";
import { UnknownSchema } from "./unknown";
import { NeverSchema } from "./never";
import { LiteralSchema } from "./literal";
import { TupleSchema } from "./tuple";
import { DateSchema } from "./date";
import { NativeEnumSchema } from "./native-enum";
import { LazySchema } from "./lazy";
import { GlobalRegistry } from "./registry";
import { toJSONSchema } from "./json-schema";

type AnyS = Schema<SchemaDef, any>;
type AnySMap = Record<string, AnyS>;
type AnySArray = readonly AnyS[];

function objectFactory<Shape extends AnySMap>(shape: Shape) {
  return new ObjectSchema<InferObject<Shape>>(shape);
}
function stringFactory<T extends string = string>() {
  return new StringSchema<T>();
}
function numberFactory<T extends number = number>() {
  return new NumberSchema<T>();
}
function booleanFactory<T extends boolean = boolean>() {
  return new BooleanSchema<T>();
}
function enumFactory<T extends string, Values extends readonly [T, ...T[]]>(values: Values) {
  return new EnumSchema<Values[number]>(values);
}
function unionFactory<Options extends AnySArray>(options: Options) {
  return new UnionSchema<InferUnion<Options>>(options);
}
function arrayFactory<Element extends AnyS>(element: Element) {
  return new ArraySchema<Infer<Element>[]>(element);
}
function recordFactory<Key extends AnyS, Value extends AnyS>(keyType: Key, valueType: Value) {
  return new RecordSchema<Record<Infer<Key>, Infer<Value>>>(keyType, valueType);
}
function literalFactory<T>(value: T) {
  return new LiteralSchema<T>(value);
}
function tupleFactory<Items extends AnySArray>(items: Items) {
  return new TupleSchema<InferTuple<Items>>(items);
}

export const z = {
  object: objectFactory,
  string: stringFactory,
  number: numberFactory,
  boolean: booleanFactory,
  enum: enumFactory,
  union: unionFactory,
  array: arrayFactory,
  record: recordFactory,
  any: () => new AnySchema(),
  unknown: () => new UnknownSchema(),
  never: () => new NeverSchema(),
  literal: literalFactory,
  tuple: tupleFactory,
  date: () => new DateSchema(),
  nativeEnum: (enumObject: Record<string, string | number>) => new NativeEnumSchema(enumObject),
  lazy: (getter: () => Schema) => new LazySchema(getter),
  globalRegistry: new GlobalRegistry(),
  toJSONSchema,
};

import type { Schema, SchemaDef } from "./core";

export type Infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;

export type InferObject<Shape extends Record<string, Schema<SchemaDef, any>>> = {
  [K in keyof Shape]: Infer<Shape[K]>
};

export type InferTuple<Items extends readonly Schema<SchemaDef, any>[]> = {
  [K in keyof Items]: Infer<Items[K]>
};

export type InferUnion<Options extends readonly Schema<SchemaDef, any>[]> =
  InferTuple<Options>[number];

export type IsAny<T> = 0 extends (1 & T) ? true : false;

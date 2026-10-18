import type { Schema, SchemaDef } from "./core";
import type { ObjectSchema } from "./object";

export type ZodType<T = any> = Schema<SchemaDef, T>;
export type ZodObject<T = any> = ObjectSchema<T>;
export type ZodTypeAny = Schema<SchemaDef, any>;

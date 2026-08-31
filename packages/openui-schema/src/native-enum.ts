import { Schema, type SchemaDef } from "./core";

export interface NativeEnumDef extends SchemaDef {
  type: "nativeEnum";
  values: (string | number)[];
}

export class NativeEnumSchema<T = any> extends Schema<NativeEnumDef, T> {
  constructor(enumObject: Record<string, string | number>) {
    const values = Object.values(enumObject).filter(
      (v) => typeof v === "string" || typeof v === "number",
    ) as (string | number)[];
    super({ type: "nativeEnum", values });
  }
}

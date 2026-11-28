import { Schema, type SchemaDef } from "./core";

export interface EnumDef extends SchemaDef {
  type: "enum";
  values: string[];
}

export class EnumSchema<T extends string = string> extends Schema<EnumDef, T> {
  constructor(values: readonly string[]) {
    super({ type: "enum", values: [...values] });
  }
}

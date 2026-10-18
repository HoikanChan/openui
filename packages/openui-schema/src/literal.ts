import { Schema, type SchemaDef } from "./core";

export interface LiteralDef extends SchemaDef {
  type: "literal";
  values: [unknown];
}

export class LiteralSchema<T = unknown> extends Schema<LiteralDef, T> {
  constructor(value: unknown) {
    super({ type: "literal", values: [value] });
  }
}

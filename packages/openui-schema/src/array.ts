import { Schema, type SchemaDef } from "./core";

export interface ArrayDef extends SchemaDef {
  type: "array";
  element: Schema;
}

export class ArraySchema<T = unknown> extends Schema<ArrayDef, T> {
  constructor(element: Schema) {
    super({ type: "array", element });
  }
}

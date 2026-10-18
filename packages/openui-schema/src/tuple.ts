import { Schema, type SchemaDef } from "./core";

export interface TupleDef extends SchemaDef {
  type: "tuple";
  items: Schema[];
}

export class TupleSchema<T = unknown> extends Schema<TupleDef, T> {
  constructor(items: readonly Schema[]) {
    super({ type: "tuple", items: [...items] });
  }
}

import { Schema, type SchemaDef } from "./core";

export interface AnyDef extends SchemaDef {
  type: "any";
}

export class AnySchema<T = any> extends Schema<AnyDef, T> {
  constructor() {
    super({ type: "any" });
  }
}

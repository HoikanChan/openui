import { Schema, type SchemaDef } from "./core";

export interface UnknownDef extends SchemaDef {
  type: "unknown";
}

export class UnknownSchema<T = unknown> extends Schema<UnknownDef, T> {
  constructor() {
    super({ type: "unknown" });
  }
}

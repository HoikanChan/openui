import { Schema, type SchemaDef } from "./core";

export interface NeverDef extends SchemaDef {
  type: "never";
}

export class NeverSchema extends Schema<NeverDef, never> {
  constructor() {
    super({ type: "never" });
  }
}

import { Schema, type SchemaDef } from "./core";

export interface UnionDef extends SchemaDef {
  type: "union";
  options: Schema[];
}

export class UnionSchema<T = unknown> extends Schema<UnionDef, T> {
  constructor(options: readonly Schema[]) {
    super({ type: "union", options: [...options] });
  }

  get options(): Schema[] {
    return this._def.options;
  }
}

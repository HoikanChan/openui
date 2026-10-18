import { Schema, type SchemaDef } from "./core";

export interface LazyDef extends SchemaDef {
  type: "lazy";
  getter: () => Schema;
}

export class LazySchema<T = any> extends Schema<LazyDef, T> {
  private cached?: Schema;

  constructor(getter: () => Schema) {
    super({ type: "lazy", getter });
  }

  get innerType(): Schema {
    if (!this.cached) {
      this.cached = this._def.getter();
    }
    return this.cached;
  }
}

import type { Registry, Schema } from "./core";

export class GlobalRegistry implements Registry {
  private store = new WeakMap<object, { id: string }>();

  register(schema: Schema, meta: { id: string }): void {
    this.store.set(schema, meta);
  }

  get(schema: Schema): { id: string } | undefined {
    return this.store.get(schema);
  }

  has(schema: Schema): boolean {
    return this.store.has(schema);
  }
}

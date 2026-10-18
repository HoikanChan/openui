import { Schema, type SchemaDef } from "./core";
import type { InferObject } from "./infer";

export interface ObjectDef extends SchemaDef {
  type: "object";
  shape: Record<string, Schema>;
  strict?: boolean;
}

export class ObjectSchema<T = any> extends Schema<ObjectDef, T> {
  constructor(shape: Record<string, Schema>, strict?: boolean) {
    super({ type: "object", shape, strict: strict ?? false });
  }

  get shape(): Record<string, Schema> {
    return this._def.shape;
  }

  strict(): ObjectSchema<T> {
    return new ObjectSchema(this._def.shape, true);
  }

  merge(other: ObjectSchema): ObjectSchema {
    const mergedShape = { ...this._def.shape, ...other._def.shape };
    return new ObjectSchema(mergedShape, this._def.strict || other._def.strict);
  }
}

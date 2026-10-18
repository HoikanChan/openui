import { Schema, type SchemaDef } from "./core";

export interface RecordDef extends SchemaDef {
  type: "record";
  keyType: Schema;
  valueType: Schema;
}

export class RecordSchema<T = unknown> extends Schema<RecordDef, T> {
  constructor(keyType: Schema, valueType: Schema) {
    super({ type: "record", keyType, valueType });
  }
}

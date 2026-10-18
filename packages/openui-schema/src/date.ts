import { Schema, type SchemaDef } from "./core";

export interface DateDef extends SchemaDef {
  type: "date";
}

export class DateSchema extends Schema<DateDef, Date> {
  constructor() {
    super({ type: "date" });
  }
}

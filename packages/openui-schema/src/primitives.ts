import { Schema, type SchemaDef } from "./core";

export interface StringDef extends SchemaDef {
  type: "string";
  pattern?: string;
  format?: "uri" | "email";
}

export class StringSchema<T = string> extends Schema<StringDef, T> {
  constructor(def: StringDef = { type: "string" }) {
    super(def);
  }

  regex(pattern: string | RegExp): StringSchema<T> {
    const p = typeof pattern === "string" ? pattern : pattern.source;
    return new StringSchema({ ...this._def, pattern: p });
  }

  url(): StringSchema<T> {
    return new StringSchema({ ...this._def, format: "uri" });
  }

  email(): StringSchema<T> {
    return new StringSchema({ ...this._def, format: "email" });
  }
}

export interface NumberDef extends SchemaDef {
  type: "number";
  int?: boolean;
  positive?: boolean;
  min?: number;
  max?: number;
}

export class NumberSchema<T = number> extends Schema<NumberDef, T> {
  constructor(def: NumberDef = { type: "number" }) {
    super(def);
  }

  int(): NumberSchema<T> {
    return new NumberSchema({ ...this._def, int: true });
  }

  positive(): NumberSchema<T> {
    return new NumberSchema({ ...this._def, positive: true });
  }

  min(n: number): NumberSchema<T> {
    return new NumberSchema({ ...this._def, min: n });
  }

  max(n: number): NumberSchema<T> {
    return new NumberSchema({ ...this._def, max: n });
  }
}

export interface BooleanDef extends SchemaDef {
  type: "boolean";
}

export class BooleanSchema<T = boolean> extends Schema<BooleanDef, T> {
  constructor() {
    super({ type: "boolean" });
  }
}

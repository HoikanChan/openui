import type { SafeParseResult } from "./validate";
import { validate } from "./validate";

export interface SchemaDef {
  type: string;
}

export interface WrapperDef extends SchemaDef {
  innerType: Schema;
}

export interface OptionalDef extends WrapperDef {
  type: "optional";
}

export interface DefaultDef extends WrapperDef {
  type: "default";
  defaultValue: unknown;
}

export interface NullableDef extends WrapperDef {
  type: "nullable";
}

export interface Registry {
  register(schema: Schema, meta: { id: string }): void;
  get(schema: Schema): { id: string } | undefined;
  has(schema: Schema): boolean;
}

export class Schema<TDef extends SchemaDef = SchemaDef, T = any> {
  readonly _def: TDef;

  constructor(def: TDef) {
    this._def = def;
  }

  optional(): OptionalSchema<T> {
    return new OptionalSchema(this);
  }

  default(defaultValue: T): DefaultSchema<T> {
    return new DefaultSchema(this, defaultValue);
  }

  nullable(): NullableSchema<T> {
    return new NullableSchema(this);
  }

  register(registry: Registry, meta: { id: string }): void {
    registry.register(this, meta);
  }

  safeParse(input: unknown): SafeParseResult<T> {
    return validate(this, input) as SafeParseResult<T>;
  }

  parse(input: unknown): T {
    const result = this.safeParse(input);
    if (!result.success) throw new Error(result.error.message);
    return result.data;
  }
}

export class OptionalSchema<T = any> extends Schema<OptionalDef, T | undefined> {
  constructor(innerType: Schema<SchemaDef, T>) {
    super({ type: "optional", innerType });
  }

  get innerType(): Schema<SchemaDef, T> {
    return this._def.innerType;
  }
}

export class DefaultSchema<T = any> extends Schema<DefaultDef, T> {
  constructor(innerType: Schema<SchemaDef, T>, defaultValue: unknown) {
    super({ type: "default", innerType, defaultValue });
  }

  get innerType(): Schema<SchemaDef, T> {
    return this._def.innerType;
  }
}

export class NullableSchema<T = any> extends Schema<NullableDef, T | null> {
  constructor(innerType: Schema<SchemaDef, T>) {
    super({ type: "nullable", innerType });
  }

  get innerType(): Schema<SchemaDef, T> {
    return this._def.innerType;
  }
}

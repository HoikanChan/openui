import type { ObjectDef } from "./object";
import type { StringDef, NumberDef, BooleanDef } from "./primitives";
import type { EnumDef } from "./enum";
import type { UnionDef } from "./union";
import type { ArrayDef } from "./array";
import type { RecordDef } from "./record";
import type { AnyDef } from "./any";
import type { UnknownDef } from "./unknown";
import type { NeverDef } from "./never";
import type { LiteralDef } from "./literal";
import type { TupleDef } from "./tuple";
import type { DateDef } from "./date";
import type { NativeEnumDef } from "./native-enum";
import type { LazyDef } from "./lazy";
import type { OptionalDef, DefaultDef, NullableDef } from "./core";

export type AnySchemaDef =
  | ObjectDef
  | StringDef
  | NumberDef
  | BooleanDef
  | EnumDef
  | UnionDef
  | ArrayDef
  | RecordDef
  | AnyDef
  | UnknownDef
  | NeverDef
  | LiteralDef
  | TupleDef
  | DateDef
  | NativeEnumDef
  | LazyDef
  | OptionalDef
  | DefaultDef
  | NullableDef;

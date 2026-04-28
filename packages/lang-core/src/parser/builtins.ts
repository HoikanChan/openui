/**
 * Shared parser/runtime registry hub for:
 *   - Runtime data builtins (evaluator.ts imports `.fn`)
 *   - Prompt builtin docs (prompt.ts imports `.signature` + `.description`)
 *   - Parser/runtime call classification (`isBuiltin`, action names, reserved calls)
 */

export interface BuiltinRuntimeContext {
  locale?: string;
}

export interface BuiltinDef {
  /** PascalCase name matching the openui-lang syntax: Count, Sum, etc. */
  name: string;
  /** Signature for prompt docs: "@Count(array) -> number" */
  signature: string;
  /** One-line description for prompt docs */
  description: string;
  /** Template/render builtins are always included in prompt docs. */
  templateBuiltin?: boolean;
  /** Runtime implementation */
  fn: (runtime: BuiltinRuntimeContext, ...args: unknown[]) => unknown;
}

export interface LazyBuiltinDef {
  signature: string;
  description: string;
  /** Template/render builtins are always included in prompt docs. */
  templateBuiltin?: boolean;
}

/** Resolve a field path on an object. Supports dot-paths: "state.name" -> obj.state.name */
function resolveField(obj: any, path: string): unknown {
  if (!path || obj == null) return undefined;
  if (!path.includes(".")) return obj[path];
  let cur = obj;
  for (const p of path.split(".")) {
    if (cur == null) return undefined;
    cur = cur[p];
  }
  return cur;
}

function toNumber(val: unknown): number {
  if (typeof val === "number") return val;
  if (typeof val === "string") {
    const n = Number(val);
    return isNaN(n) ? 0 : n;
  }
  if (typeof val === "boolean") return val ? 1 : 0;
  return 0;
}

function resolveLocale(runtime: BuiltinRuntimeContext, localeArg: unknown): string | undefined {
  if (typeof localeArg === "string" && localeArg.trim()) return localeArg;
  return runtime.locale;
}

function parseFiniteNumber(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function parseDateLike(value: unknown): Date | null {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  if (typeof value !== "string" && typeof value !== "number") return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  if (value == null || typeof value !== "object" || Array.isArray(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function objectMapEntries(
  value: unknown,
): Array<{
  key: string;
  value: unknown;
}> {
  if (!isPlainRecord(value)) return [];
  return Object.entries(value).map(([key, entryValue]) => ({ key, value: entryValue }));
}

function formatNumberValue(
  value: number,
  locale: string | undefined,
  options: Intl.NumberFormatOptions,
): string {
  return new Intl.NumberFormat(locale, options).format(value);
}

function formatNumberish(
  value: unknown,
  runtime: BuiltinRuntimeContext,
  decimalsArg: unknown,
  localeArg: unknown,
  options: Intl.NumberFormatOptions = {},
): string {
  if (value == null) return "";

  const parsed = parseFiniteNumber(value);
  if (parsed == null) return String(value);

  const locale = resolveLocale(runtime, localeArg);
  const decimals = decimalsArg == null ? null : parseFiniteNumber(decimalsArg);
  if (decimalsArg != null && decimals == null) return String(value);

  try {
    if (decimals != null) {
      const normalized = Math.max(0, Math.floor(decimals));
      return formatNumberValue(parsed, locale, {
        ...options,
        minimumFractionDigits: normalized,
        maximumFractionDigits: normalized,
      });
    }

    return formatNumberValue(parsed, locale, options);
  } catch {
    return String(value);
  }
}

function formatDateBuiltin(
  value: unknown,
  styleArg: unknown,
  localeArg: unknown,
  runtime: BuiltinRuntimeContext,
): string {
  if (value == null) return "";

  const date = parseDateLike(value);
  if (!date) return String(value);

  const style = typeof styleArg === "string" && styleArg ? styleArg : "dateTime";
  const locale = resolveLocale(runtime, localeArg);

  try {
    if (style === "relative") {
      const diffMs = date.getTime() - Date.now();
      const absMs = Math.abs(diffMs);
      const steps: Array<[Intl.RelativeTimeFormatUnit, number]> = [
        ["year", 365 * 24 * 60 * 60 * 1000],
        ["month", 30 * 24 * 60 * 60 * 1000],
        ["week", 7 * 24 * 60 * 60 * 1000],
        ["day", 24 * 60 * 60 * 1000],
        ["hour", 60 * 60 * 1000],
        ["minute", 60 * 1000],
        ["second", 1000],
      ];

      const [unit, size] = steps.find(([, threshold]) => absMs >= threshold) ?? ["second", 1000];
      const relativeValue = Math.round(diffMs / size);
      return new Intl.RelativeTimeFormat(locale, { numeric: "auto" }).format(
        relativeValue,
        unit,
      );
    }

    const optionsByStyle: Record<string, Intl.DateTimeFormatOptions> = {
      date: { dateStyle: "medium" },
      dateTime: { dateStyle: "medium", timeStyle: "short" },
      time: { timeStyle: "short" },
    };

    const options = optionsByStyle[style];
    if (!options) return String(value);
    return new Intl.DateTimeFormat(locale, options).format(date);
  } catch {
    return String(value);
  }
}

function formatBytesBuiltin(
  value: unknown,
  localeArg: unknown,
  runtime: BuiltinRuntimeContext,
): string {
  if (value == null) return "";

  const parsed = parseFiniteNumber(value);
  if (parsed == null) return String(value);

  const base = 1000;
  const units = ["B", "KB", "MB", "GB", "TB", "PB"];

  let unitIndex = 0;
  let nextValue = Math.abs(parsed);
  while (nextValue >= base && unitIndex < units.length - 1) {
    nextValue /= base;
    unitIndex += 1;
  }

  const scaled = parsed / Math.pow(base, unitIndex);
  const locale = resolveLocale(runtime, localeArg);

  try {
    const digits = unitIndex === 0 ? 0 : 1;
    const number = formatNumberValue(scaled, locale, {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    });
    return `${number} ${units[unitIndex]}`;
  } catch {
    return String(value);
  }
}

function formatDurationUnit(
  value: number,
  unit: "day" | "hour" | "minute" | "second" | "millisecond",
  locale: string | undefined,
): string {
  return new Intl.NumberFormat(locale, {
    style: "unit",
    unit,
    unitDisplay: "narrow",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDurationBuiltin(
  value: unknown,
  unitArg: unknown,
  localeArg: unknown,
  runtime: BuiltinRuntimeContext,
): string {
  if (value == null) return "";

  const parsed = parseFiniteNumber(value);
  if (parsed == null) return String(value);

  const inputUnit = unitArg === "ms" ? "ms" : unitArg === "s" || unitArg == null ? "s" : null;
  if (!inputUnit) return String(value);

  const locale = resolveLocale(runtime, localeArg);
  let remainingMs = Math.round(Math.abs(parsed) * (inputUnit === "ms" ? 1 : 1000));
  const sign = parsed < 0 ? "-" : "";

  try {
    const parts: string[] = [];
    const units: Array<[number, "day" | "hour" | "minute" | "second" | "millisecond"]> = [
      [24 * 60 * 60 * 1000, "day"],
      [60 * 60 * 1000, "hour"],
      [60 * 1000, "minute"],
      [1000, "second"],
      [1, "millisecond"],
    ];

    for (const [size, unit] of units) {
      if (remainingMs < size && parts.length === 0 && unit !== "millisecond") continue;
      const amount = unit === "millisecond" ? remainingMs : Math.floor(remainingMs / size);
      if (amount <= 0) continue;
      parts.push(formatDurationUnit(amount, unit, locale));
      remainingMs -= amount * size;
      if (parts.length === 2) break;
    }

    if (parts.length === 0) {
      parts.push(formatDurationUnit(0, inputUnit === "ms" ? "millisecond" : "second", locale));
    }

    if (sign && parts[0]) {
      parts[0] = `${sign}${parts[0]}`;
    }

    return parts.join(" ");
  } catch {
    return String(value);
  }
}

export const BUILTINS: Record<string, BuiltinDef> = {
  Count: {
    name: "Count",
    signature: "Count(array) -> number",
    description: "Returns array length",
    fn: (_runtime, arr) => (Array.isArray(arr) ? arr.length : 0),
  },
  First: {
    name: "First",
    signature: "First(array) -> element",
    description: "Returns first element of array",
    fn: (_runtime, arr) => (Array.isArray(arr) ? (arr[0] ?? null) : null),
  },
  Last: {
    name: "Last",
    signature: "Last(array) -> element",
    description: "Returns last element of array",
    fn: (_runtime, arr) => (Array.isArray(arr) ? (arr[arr.length - 1] ?? null) : null),
  },
  Sum: {
    name: "Sum",
    signature: "Sum(numbers[]) -> number",
    description: "Sum of numeric array",
    fn: (_runtime, arr) =>
      Array.isArray(arr) ? arr.reduce((a: number, b: unknown) => a + toNumber(b), 0) : 0,
  },
  Avg: {
    name: "Avg",
    signature: "Avg(numbers[]) -> number",
    description: "Average of numeric array",
    fn: (_runtime, arr) =>
      Array.isArray(arr) && arr.length
        ? (arr.reduce((a: number, b: unknown) => a + toNumber(b), 0) as number) / arr.length
        : 0,
  },
  Min: {
    name: "Min",
    signature: "Min(numbers[]) -> number",
    description: "Minimum value in array",
    fn: (_runtime, arr) =>
      Array.isArray(arr) && arr.length
        ? arr.reduce((acc: number, b: unknown) => Math.min(acc, toNumber(b)), toNumber(arr[0]))
        : 0,
  },
  Max: {
    name: "Max",
    signature: "Max(numbers[]) -> number",
    description: "Maximum value in array",
    fn: (_runtime, arr) =>
      Array.isArray(arr) && arr.length
        ? arr.reduce((acc: number, b: unknown) => Math.max(acc, toNumber(b)), toNumber(arr[0]))
        : 0,
  },
  Sort: {
    name: "Sort",
    signature: "Sort(array, field, direction?) -> sorted array",
    description: 'Sort array by field. Direction: "asc" (default) or "desc"',
    fn: (_runtime, arr, field, dir) => {
      if (!Array.isArray(arr)) return arr;
      const f = String(field ?? "");
      const desc = String(dir ?? "asc") === "desc";
      return [...arr].sort((a: any, b: any) => {
        const av = f ? resolveField(a, f) : a;
        const bv = f ? resolveField(b, f) : b;
        const aIsNumeric =
          typeof av === "number" || (typeof av === "string" && !isNaN(Number(av)) && av !== "");
        const bIsNumeric =
          typeof bv === "number" || (typeof bv === "string" && !isNaN(Number(bv)) && bv !== "");
        if (aIsNumeric && bIsNumeric) {
          const diff = toNumber(av) - toNumber(bv);
          return desc ? -diff : diff;
        }
        const cmp = String(av ?? "").localeCompare(String(bv ?? ""));
        return desc ? -cmp : cmp;
      });
    },
  },
  Filter: {
    name: "Filter",
    signature:
      'Filter(array, field, operator: "==" | "!=" | ">" | "<" | ">=" | "<=" | "contains", value) -> filtered array',
    description: "Filter array by field value",
    fn: (_runtime, arr, field, op, value) => {
      if (!Array.isArray(arr)) return [];
      const f = String(field ?? "");
      const o = String(op ?? "==");
      return arr.filter((item: any) => {
        const v = f ? resolveField(item, f) : item;
        switch (o) {
          case "==":
            return v == value;
          case "!=":
            return v != value;
          case ">":
            return toNumber(v) > toNumber(value);
          case "<":
            return toNumber(v) < toNumber(value);
          case ">=":
            return toNumber(v) >= toNumber(value);
          case "<=":
            return toNumber(v) <= toNumber(value);
          case "contains":
            return String(v ?? "").includes(String(value ?? ""));
          default:
            return false;
        }
      });
    },
  },
  ObjectEntries: {
    name: "ObjectEntries",
    signature: "ObjectEntries(obj) -> {key: string, value: any}[]",
    description:
      "Convert a plain object map into ordered rows of {key, value}. Returns [] for null, arrays, and unsupported inputs.",
    fn: (_runtime, obj) => objectMapEntries(obj),
  },
  ObjectKeys: {
    name: "ObjectKeys",
    signature: "ObjectKeys(obj) -> string[]",
    description:
      "Return the ordered keys of a plain object map. Returns [] for null, arrays, and unsupported inputs.",
    fn: (_runtime, obj) => objectMapEntries(obj).map((entry) => entry.key),
  },
  Round: {
    name: "Round",
    signature: "Round(number, decimals?) -> number",
    description: "Round to N decimal places (default 0)",
    fn: (_runtime, n, decimals) => {
      const num = toNumber(n);
      const d = decimals != null ? toNumber(decimals) : 0;
      const factor = Math.pow(10, d);
      return Math.round(num * factor) / factor;
    },
  },
  Abs: {
    name: "Abs",
    signature: "Abs(number) -> number",
    description: "Absolute value",
    fn: (_runtime, n) => Math.abs(toNumber(n)),
  },
  Floor: {
    name: "Floor",
    signature: "Floor(number) -> number",
    description: "Round down to nearest integer",
    fn: (_runtime, n) => Math.floor(toNumber(n)),
  },
  Ceil: {
    name: "Ceil",
    signature: "Ceil(number) -> number",
    description: "Round up to nearest integer",
    fn: (_runtime, n) => Math.ceil(toNumber(n)),
  },
  Switch: {
    name: "Switch",
    signature: "Switch(value, cases, default?) -> result",
    description:
      "Map an enum value to a display result via a cases object. Numeric values are coerced to strings for key lookup. Returns default (null if omitted) when no case matches.",
    templateBuiltin: true,
    fn: (_runtime, value, cases, defaultVal) => {
      if (cases == null || typeof cases !== "object" || Array.isArray(cases)) {
        return defaultVal ?? null;
      }
      const key = String(value ?? "");
      return key in (cases as Record<string, unknown>)
        ? (cases as Record<string, unknown>)[key]
        : (defaultVal ?? null);
    },
  },
  FormatDate: {
    name: "FormatDate",
    signature: 'FormatDate(value, style?, locale?) -> string | string[]',
    description:
      'Format a date-like value for display. Styles: "date", "dateTime", "time", or "relative". Accepts an array and maps over each element. Returns "" for nullish input and String(value) for invalid dates.',
    fn: (runtime, value, style, locale) => {
      if (Array.isArray(value)) {
        return value.map((item) => formatDateBuiltin(item, style, locale, runtime));
      }
      return formatDateBuiltin(value, style, locale, runtime);
    },
  },
  FormatBytes: {
    name: "FormatBytes",
    signature: 'FormatBytes(value) -> string',
    description:
      'Format a byte count into a compact SI string (KB, MB, GB). Returns "" for nullish input.',
    fn: (runtime, value, locale) => formatBytesBuiltin(value, locale, runtime),
  },
  FormatNumber: {
    name: "FormatNumber",
    signature: "FormatNumber(value, decimals?, locale?) -> string",
    description:
      "Format a scalar number as a locale-aware display string. Uses the renderer locale by default and returns raw input for invalid values.",
    fn: (runtime, value, decimals, locale) =>
      formatNumberish(value, runtime, decimals, locale),
  },
  FormatPercent: {
    name: "FormatPercent",
    signature: "FormatPercent(value, decimals?, locale?) -> string",
    description:
      "Format a ratio such as 0.125 as a locale-aware percentage display string, with optional decimal precision and locale override.",
    fn: (runtime, value, decimals, locale) =>
      formatNumberish(value, runtime, decimals, locale, { style: "percent" }),
  },
  FormatDuration: {
    name: "FormatDuration",
    signature: 'FormatDuration(value, unit?, locale?) -> string',
    description:
      'Format an elapsed duration into a compact display string. Input defaults to seconds and also supports explicit "ms" input.',
    fn: (runtime, value, unit, locale) => formatDurationBuiltin(value, unit, locale, runtime),
  },
};

/**
 * Lazy builtins - these receive AST nodes (not evaluated values) and
 * control their own evaluation. Handled specially in evaluator.ts.
 */
export const LAZY_BUILTINS: Set<string> = new Set(["Each", "Render"]);

export const LAZY_BUILTIN_DEFS: Record<string, LazyBuiltinDef> = {
  Each: {
    signature: "Each(array, varName, template)",
    description:
      "Evaluate template for each element. varName is the loop variable - use it ONLY inside the template expression (inline). Do NOT create a separate statement for the template.",
    templateBuiltin: true,
  },
  Render: {
    signature: 'Render("v", expr) / Render("v", "row", expr)',
    description:
      "Create a deferred render template for prop values. Binder names are string literals and are only in scope inside the template body.",
    templateBuiltin: true,
  },
};

/** Maps parser-level action step names -> runtime step type values. Single source of truth. */
export const ACTION_STEPS = {
  Run: "run",
  ToAssistant: "continue_conversation",
  OpenUrl: "open_url",
  Set: "set",
  Reset: "reset",
} as const;

/** All action expression names (steps + the Action container) */
export const ACTION_NAMES: Set<string> = new Set(["Action", ...Object.keys(ACTION_STEPS)]);

/** Set of builtin names for fast lookup (includes action expressions) */
export const BUILTIN_NAMES: Set<string> = new Set([
  ...Object.keys(BUILTINS),
  ...LAZY_BUILTINS,
  ...ACTION_NAMES,
]);

/** Check if a name is a builtin function (not a component) */
export function isBuiltin(name: string): boolean {
  return BUILTIN_NAMES.has(name);
}

/** Reserved statement-level call names - not builtins, not components */
export const RESERVED_CALLS = { Query: "Query", Mutation: "Mutation" } as const;

/** Check if a name is a reserved statement call (Query, Mutation) */
export function isReservedCall(name: string): boolean {
  return name in RESERVED_CALLS;
}

/** Re-export toNumber for evaluator compatibility */
export { toNumber };

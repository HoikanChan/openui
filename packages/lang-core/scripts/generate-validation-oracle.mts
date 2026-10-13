// Regenerates the cross-language VALIDATION oracle (Task 8 / design D10).
//
// TypeScript's `packages/lang-core` parser is the ORACLE for the Java validator's
// contract-error taxonomy on the SUPPORTED SUBSET (this is NOT full AST parity).
//
// For a small, representative set of parity CASES — each a `{ name, dsl, contract }`
// where `contract` is a map of component name → propsSchema (JSON-Schema `object`
// form, identical to the Java `ComponentPromptSpec.propsSchema`) — this script:
//   1. converts each contract into the TS `LibraryJSONSchema` ($defs) and lets the
//      parser's own `compileSchema` build the `ParamMap` (reused, not re-implemented),
//   2. runs `parse(dsl, paramMap, "root")`,
//   3. records the observable contract: `status`, `errors[]` (code/component/path/
//      statementId), `unresolved[]` names, and whether a `root` element resolved,
//   4. writes `oracle.json` into the Java SDK's test resources.
//
// The Java `CrossLanguageParityTest` loads that COMMITTED `oracle.json` from its
// classpath and asserts parity — the Java build runs NO Node.
//
// GENERATED ARTIFACT: `oracle.json` is regen-only. Do NOT hand-edit it; change the
// CASES here and re-run:  pnpm --dir packages/lang-core run generate:validation-oracle
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createParser } from "../src/parser/parser";
import type { LibraryJSONSchema } from "../src/parser/types";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "../../..");
const outFile = resolve(
  repoRoot,
  "packages/genui-java-sdk/src/test/resources/parity/oracle.json",
);

/** A component's propsSchema — the SAME object both languages consume as the contract. */
type PropsSchema = Record<string, unknown>;
/** Contract = component name → propsSchema. Insertion order = positional-arg order. */
type Contract = Record<string, PropsSchema>;

interface Case {
  name: string;
  dsl: string;
  contract: Contract;
}

// ── Shared component schemas ──────────────────────────────────────────────────
// Mirrors the catalog in Java SemanticValidationTest: Header(title, subtitle?),
// Table(columns, rows), Col(title, field, options?) where options is a closed object.
const S = {
  string: { type: "string" },
  array: { type: "array" },
};

const HEADER: PropsSchema = {
  type: "object",
  properties: { title: S.string, subtitle: S.string },
  required: ["title"],
};

const TABLE: PropsSchema = {
  type: "object",
  properties: { columns: S.array, rows: S.array },
  required: ["columns", "rows"],
};

// Col.options is a CLOSED object (additionalProperties:false) so the removed
// `format` key is flagged as invalid-prop with the special hint.
const COL: PropsSchema = {
  type: "object",
  properties: {
    title: S.string,
    field: S.string,
    options: {
      type: "object",
      additionalProperties: false,
      properties: { sortable: { type: "boolean" } },
    },
  },
  required: ["title", "field"],
};

const CATALOG: Contract = { Header: HEADER, Table: TABLE, Col: COL };

// ── Parity cases (design §8.2) ────────────────────────────────────────────────
const CASES: Case[] = [
  {
    name: "valid-syntax",
    dsl: 'root = Header("Sales", "Q3")',
    contract: CATALOG,
  },
  {
    name: "unknown-component",
    dsl: 'root = Bogus("x")',
    contract: CATALOG,
  },
  {
    name: "missing-required",
    dsl: "root = Header()",
    contract: CATALOG,
  },
  {
    name: "null-required",
    dsl: "root = Header(null)",
    contract: CATALOG,
  },
  {
    name: "invalid-nested-prop",
    dsl: 'root = Col("Name", "name", { format: "date" })',
    contract: CATALOG,
  },
  {
    name: "excess-args",
    dsl: 'root = Header("Sales", "Q3", "extra")',
    contract: CATALOG,
  },
  {
    name: "final-unresolved-ref",
    dsl: "root = Table(cols, [])",
    contract: CATALOG,
  },
  {
    name: "syntax-failure-unclosed",
    dsl: 'root = Header("Sales"',
    contract: CATALOG,
  },
  {
    name: "multi-statement-with-refs",
    dsl: 'header = Header("Sales")\nroot = Table([header], [])',
    contract: CATALOG,
  },
];

// ── Contract → LibraryJSONSchema ($defs), then reuse compileSchema via createParser ──
function toLibrarySchema(contract: Contract): LibraryJSONSchema {
  const defs: NonNullable<LibraryJSONSchema["$defs"]> = {};
  for (const [name, propsSchema] of Object.entries(contract)) {
    const properties =
      propsSchema.properties && typeof propsSchema.properties === "object"
        ? (propsSchema.properties as Record<string, unknown>)
        : {};
    const required = Array.isArray(propsSchema.required)
      ? (propsSchema.required as string[])
      : [];
    defs[name] = { properties, required };
  }
  return { $defs: defs };
}

interface OracleError {
  code: string;
  component: string;
  path: string;
  statementId: string | null;
}

interface OracleEntry {
  name: string;
  dsl: string;
  contract: Contract;
  expected: {
    status: "valid" | "invalid" | "no-root";
    errors: OracleError[];
    unresolved: string[];
    hasRoot: boolean;
  };
}

const entries: OracleEntry[] = CASES.map((c) => {
  const parser = createParser(toLibrarySchema(c.contract), "root");
  const result = parser.parse(c.dsl);

  const errors: OracleError[] = result.meta.errors.map((e) => ({
    code: e.code,
    component: e.component,
    path: e.path,
    statementId: e.statementId ?? null,
  }));
  const hasRoot = result.root != null;
  // Status: any errors → invalid; else no root resolved → no-root; else valid.
  const status: OracleEntry["expected"]["status"] =
    errors.length > 0 ? "invalid" : hasRoot ? "valid" : "no-root";

  return {
    name: c.name,
    dsl: c.dsl,
    contract: c.contract,
    expected: {
      status,
      errors,
      unresolved: [...result.meta.unresolved],
      hasRoot,
    },
  };
});

const banner =
  "// GENERATED by packages/lang-core/scripts/generate-validation-oracle.mts — DO NOT EDIT.\n" +
  "// Regenerate: pnpm --dir packages/lang-core run generate:validation-oracle\n";

// oracle.json must be pure JSON (Java reads it via fastjson2), so the provenance note
// lives in a leading string field, not a comment.
const payload = {
  _generated: banner.trim(),
  cases: entries,
};

await mkdir(dirname(outFile), { recursive: true });
await writeFile(outFile, `${JSON.stringify(payload, null, 2)}\n`, "utf8");
console.log(`Wrote ${entries.length} cases to ${outFile}`);
for (const e of entries) {
  console.log(
    `  ${e.name}: status=${e.expected.status} errors=[${e.expected.errors
      .map((x) => x.code)
      .join(",")}] unresolved=[${e.expected.unresolved.join(",")}] hasRoot=${e.expected.hasRoot}`,
  );
}

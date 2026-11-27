/**
 * Compare the renderer's live spec (dslLibrary.toSpec()) against the Java SDK
 * base-contract.json — the single source of truth.
 *
 * Reports: components missing from the renderer, renderer-only components, and
 * prop-level schema mismatches. Exits non-zero when any contract component is
 * missing from the renderer (prop mismatches are reported but do not fail the
 * build, so the check can run while alignment is in progress).
 */
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { dslLibrary } from "../src/genui-lib/dslLibrary";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(scriptDir, "..");
const repoRoot = resolve(packageRoot, "../..");
const contractPath = resolve(
  repoRoot,
  "packages/genui-java-sdk/src/main/resources/openui/base-contract.json",
);

// Canvas-host internals are registered in the library but are not part of the
// model-facing contract; they never appear in generated DSL.
const RENDERER_INTERNAL = new Set(["CanvasCard", "PreviewCard", "HTMLLoader"]);

interface Schema {
  type?: string;
  enum?: unknown[];
  required?: string[];
  properties?: Record<string, Schema>;
  anyOf?: Schema[];
  items?: Schema;
  component?: string;
}

const contract = JSON.parse(readFileSync(contractPath, "utf8")) as {
  contractVersion: string;
  components: Record<string, { propsSchema?: Schema }>;
};
const spec = dslLibrary.toSpec() as unknown as {
  components: Record<string, { propsSchema?: Schema }>;
};

const contractNames = Object.keys(contract.components);
const rendererNames = Object.keys(spec.components).filter((n) => !RENDERER_INTERNAL.has(n));

const missing = contractNames.filter((n) => !spec.components[n]);
const rendererOnly = rendererNames.filter((n) => !contract.components[n]);

function normEnum(e: unknown[] | undefined): string {
  return JSON.stringify([...(e ?? [])].sort());
}

function diffProps(name: string, contractSchema: Schema, rendererSchema: Schema): string[] {
  const out: string[] = [];
  const cProps = contractSchema.properties ?? {};
  const rProps = rendererSchema.properties ?? {};
  const cReq = new Set(contractSchema.required ?? []);
  const rReq = new Set(rendererSchema.required ?? []);

  for (const p of Object.keys(cProps)) {
    if (!(p in rProps)) {
      out.push(`  - prop "${p}" in contract but not renderer`);
      continue;
    }
    const cs = cProps[p];
    const rs = rProps[p];
    if (normEnum(cs.enum) !== normEnum(rs.enum) && (cs.enum || rs.enum)) {
      out.push(`  ~ prop "${p}" enum contract=${normEnum(cs.enum)} renderer=${normEnum(rs.enum)}`);
    }
    if (cReq.has(p) !== rReq.has(p)) {
      out.push(`  ~ prop "${p}" required contract=${cReq.has(p)} renderer=${rReq.has(p)}`);
    }
  }
  for (const p of Object.keys(rProps)) {
    if (!(p in cProps) && p !== "className" && p !== "style") {
      out.push(`  + prop "${p}" in renderer but not contract`);
    }
  }
  return out;
}

console.log(`contract: ${contract.contractVersion} (${contractNames.length} components)`);
console.log(`renderer: ${rendererNames.length} registered (internal excluded)`);
console.log();

if (missing.length) {
  console.log(`MISSING from renderer (${missing.length}):`);
  for (const n of missing) console.log(`  ${n}`);
  console.log();
}
if (rendererOnly.length) {
  console.log(`RENDERER-ONLY, not in contract (${rendererOnly.length}):`);
  for (const n of rendererOnly) console.log(`  ${n}`);
  console.log();
}

let mismatchCount = 0;
for (const n of contractNames) {
  if (!spec.components[n]) continue;
  const diffs = diffProps(n, contract.components[n].propsSchema ?? {}, spec.components[n].propsSchema ?? {});
  if (diffs.length) {
    mismatchCount++;
    console.log(`MISMATCH ${n}:`);
    for (const d of diffs) console.log(d);
    console.log();
  }
}

console.log(
  `summary: ${missing.length} missing, ${rendererOnly.length} renderer-only, ${mismatchCount} prop-mismatched`,
);
process.exit(missing.length ? 1 : 0);

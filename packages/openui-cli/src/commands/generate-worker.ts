/**
 * Worker script that bundles a user's library or extension file and outputs the
 * system prompt, JSON schema, or a registerable Extension JSON. Asset imports
 * are stubbed during bundling so React component modules can be evaluated
 * without CSS/image/font loaders.
 *
 * argv (library modes): [entryPath, exportName?, "--json-schema"?, "--prompt-options", name?]
 * argv (extension mode): [entryPath, exportName?, "--extension", "--extension-id", id?, "--version", ver?]
 * stdout: the prompt string, JSON schema, or Extension JSON
 */

import * as fs from "fs";
import * as os from "os";
import * as path from "path";

import * as esbuild from "esbuild";

// ── Main ──

interface Library {
  prompt(options?: unknown): string;
  toSpec(): object;
  toJSONSchema(): object;
}

const ASSET_RE = /\.(css|scss|less|sass|svg|png|jpe?g|gif|webp|ico|woff2?|ttf|eot)(\?.*)?$/i;

function createAssetStubPlugin(): esbuild.Plugin {
  return {
    name: "openui-asset-stub",
    setup(build) {
      build.onResolve({ filter: ASSET_RE }, (args) => {
        const assetPath = args.path.split("?")[0]!;
        const resolvedPath = path.isAbsolute(assetPath)
          ? assetPath
          : path.join(args.resolveDir, assetPath);
        return { path: resolvedPath, namespace: "openui-asset-stub" };
      });

      build.onLoad({ filter: /.*/, namespace: "openui-asset-stub" }, (args) => {
        const ext = path.extname(args.path).toLowerCase();
        const contents =
          ext === ".svg"
            ? "export default {}; export const ReactComponent = () => null;"
            : "export default {};";

        return { contents, loader: "js" };
      });
    },
  };
}

function isLibrary(value: unknown): value is Library {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  return typeof obj["prompt"] === "function" && typeof obj["toSpec"] === "function";
}

function findLibrary(mod: Record<string, unknown>, exportName?: string): Library | undefined {
  if (exportName) {
    const val = mod[exportName];
    return isLibrary(val) ? val : undefined;
  }

  for (const name of ["library", "default"]) {
    if (isLibrary(mod[name])) return mod[name];
  }

  for (const val of Object.values(mod)) {
    if (isLibrary(val)) return val;
  }

  return undefined;
}

interface PromptOptions {
  preamble?: string;
  additionalRules?: string[];
  examples?: string[];
  toolExamples?: string[];
  editMode?: boolean;
  inlineMode?: boolean;
  toolCalls?: boolean;
  bindings?: boolean;
}

function isPromptOptions(value: unknown): value is PromptOptions {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  return (
    Array.isArray(obj["examples"]) ||
    Array.isArray(obj["additionalRules"]) ||
    Array.isArray(obj["toolExamples"]) ||
    typeof obj["preamble"] === "string" ||
    typeof obj["editMode"] === "boolean" ||
    typeof obj["inlineMode"] === "boolean" ||
    typeof obj["toolCalls"] === "boolean" ||
    typeof obj["bindings"] === "boolean"
  );
}

function findPromptOptions(
  mod: Record<string, unknown>,
  exportName?: string,
): PromptOptions | undefined {
  if (exportName) {
    const val = mod[exportName];
    return isPromptOptions(val) ? val : undefined;
  }

  // Check well-known names first
  for (const name of ["promptOptions", "options"]) {
    if (isPromptOptions(mod[name])) return mod[name] as PromptOptions;
  }

  // Check any export ending with "PromptOptions" or "promptOptions"
  for (const [key, val] of Object.entries(mod)) {
    if (/[Pp]rompt[Oo]ptions$/.test(key) && isPromptOptions(val)) return val;
  }

  return undefined;
}

// ── Extension definition object ──

const STUB_CREATE_LIBRARY = "__openuiCreateLibrary";

interface DefinedComponentLike {
  name: string;
  props: unknown;
}

interface ExtensionObject {
  extensionId?: string;
  version?: string;
  components: DefinedComponentLike[];
  componentGroups?: unknown[];
  tools?: unknown[];
  examples?: unknown[];
  additionalRules?: unknown[];
}

interface GenerationContractLike {
  components: Record<string, unknown>;
  componentGroups?: unknown[];
  tools?: unknown[];
  examples?: unknown[];
  additionalRules?: unknown[];
}

type CreateLibraryFn = (input: {
  components: unknown[];
  componentGroups?: unknown[];
  tools?: unknown[];
  examples?: unknown[];
  additionalRules?: unknown[];
}) => { toSpec(): GenerationContractLike };

/**
 * An extension object exposes `components` as a `DefinedComponent[]` (each with
 * a `name` and a zod `props`). A built `Library` exposes `components` as a
 * record, so libraries are excluded here.
 */
function isExtensionObject(value: unknown): value is ExtensionObject {
  if (typeof value !== "object" || value === null) return false;
  const comps = (value as Record<string, unknown>)["components"];
  if (!Array.isArray(comps) || comps.length === 0) return false;
  return comps.every((c) => {
    if (typeof c !== "object" || c === null) return false;
    const comp = c as Record<string, unknown>;
    return typeof comp["name"] === "string" && comp["props"] != null;
  });
}

function findExtensionObject(
  mod: Record<string, unknown>,
  exportName?: string,
): { ext?: ExtensionObject; candidates: string[] } {
  if (exportName) {
    const val = mod[exportName];
    return { ext: isExtensionObject(val) ? val : undefined, candidates: [] };
  }

  const candidates: string[] = [];
  let ext: ExtensionObject | undefined;
  for (const [key, val] of Object.entries(mod)) {
    if (key === STUB_CREATE_LIBRARY) continue;
    if (isExtensionObject(val)) {
      candidates.push(key);
      ext = val;
    }
  }
  return { ext: candidates.length === 1 ? ext : undefined, candidates };
}

// ── Bundling ──

async function bundleModule(options: esbuild.BuildOptions): Promise<Record<string, unknown>> {
  const bundleDir = fs.mkdtempSync(path.join(os.tmpdir(), "openui-generate-"));
  const bundlePath = path.join(bundleDir, "entry.cjs");
  try {
    await esbuild.build({
      absWorkingDir: process.cwd(),
      bundle: true,
      format: "cjs",
      outfile: bundlePath,
      platform: "node",
      plugins: [createAssetStubPlugin()],
      sourcemap: "inline",
      target: "node18",
      write: true,
      ...options,
    });
    return require(bundlePath) as Record<string, unknown>;
  } finally {
    fs.rmSync(bundleDir, { force: true, recursive: true });
  }
}

// ── Modes ──

function getFlagValue(args: string[], flag: string): string | undefined {
  const i = args.indexOf(flag);
  return i !== -1 ? args[i + 1] : undefined;
}

function resolveExportName(args: string[], valueFlags: string[], booleanFlags: string[]): string {
  const reserved = new Set<string>([...valueFlags, ...booleanFlags]);
  for (const vf of valueFlags) {
    const v = getFlagValue(args, vf);
    if (v) reserved.add(v);
  }
  return args.find(
    (a, i) => i > 0 && !reserved.has(a) && !valueFlags.includes(args[i - 1] ?? ""),
  ) as string;
}

/**
 * Bundle the user entry together with `createLibrary` from
 * `@cloudsop/openui-react-lang` so the user's components and `createLibrary` share a
 * single bundled zod instance (cross-instance zod introspection is fragile).
 * Compile `components` via `createLibrary(...).toSpec()` and emit Extension JSON.
 */
async function runExtensionMode(entryPath: string, args: string[]): Promise<void> {
  const exportName = resolveExportName(args, ["--extension-id", "--version"], ["--extension"]);
  const extensionIdFlag = getFlagValue(args, "--extension-id");
  const versionFlag = getFlagValue(args, "--version");

  const entryImport = entryPath.replace(/\\/g, "/");
  const stub =
    `export * from ${JSON.stringify(entryImport)};\n` +
    `export { createLibrary as ${STUB_CREATE_LIBRARY} } from "@cloudsop/openui-react-lang";\n`;

  let mod: Record<string, unknown>;
  try {
    mod = await bundleModule({
      stdin: {
        contents: stub,
        resolveDir: process.cwd(),
        loader: "ts",
        sourcefile: "openui-extension-stub.ts",
      },
    });
  } catch (err) {
    console.error(`Error: Failed to import ${entryPath}`);
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  }

  const { ext, candidates } = findExtensionObject(mod, exportName);
  if (!ext) {
    const exports = Object.keys(mod)
      .filter((k) => k !== STUB_CREATE_LIBRARY)
      .join(", ");
    if (candidates.length > 1) {
      console.error(
        `Error: Multiple extension objects found: ${candidates.join(", ")}.\n` +
          `Use --export <name> to choose one.`,
      );
    } else {
      console.error(
        `Error: No extension object found.\n` +
          `Found exports: ${exports || "(none)"}\n` +
          `Export an object with a 'components' array (DefinedComponent[]), or use --export <name>.`,
      );
    }
    process.exit(1);
  }

  const createLibrary = mod[STUB_CREATE_LIBRARY] as CreateLibraryFn | undefined;
  if (typeof createLibrary !== "function") {
    console.error(
      "Error: Could not load createLibrary from @cloudsop/openui-react-lang.\n" +
        "Run this command inside a project that has OpenUI installed.",
    );
    process.exit(1);
  }

  let spec: GenerationContractLike;
  try {
    spec = createLibrary({
      components: ext.components,
      componentGroups: ext.componentGroups,
      tools: ext.tools,
      examples: ext.examples,
      additionalRules: ext.additionalRules,
    }).toSpec();
  } catch (err) {
    console.error(err instanceof Error ? err.message : String(err));
    process.exit(1);
  }

  const extensionId = extensionIdFlag ?? ext.extensionId;
  if (!extensionId) {
    console.error(
      "Error: extensionId is required. Set it in the extension object or pass --extension-id <id>.",
    );
    process.exit(1);
  }
  const version = versionFlag ?? ext.version ?? "";

  const out: Record<string, unknown> = {
    extensionId,
    version,
    components: spec.components,
  };
  if (spec.componentGroups?.length) out["componentGroups"] = spec.componentGroups;
  if (spec.tools?.length) out["tools"] = spec.tools;
  if (spec.examples?.length) out["examples"] = spec.examples;
  if (spec.additionalRules?.length) out["additionalRules"] = spec.additionalRules;

  process.stdout.write(JSON.stringify(out, null, 2));
}

async function runLibraryMode(entryPath: string, args: string[]): Promise<void> {
  const jsonSchema = args.includes("--json-schema");
  const promptOptionsName = getFlagValue(args, "--prompt-options");
  const exportName = resolveExportName(args, ["--prompt-options"], ["--json-schema"]);

  let mod: Record<string, unknown>;
  try {
    mod = await bundleModule({ entryPoints: [entryPath] });
  } catch (err) {
    console.error(`Error: Failed to import ${entryPath}`);
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  }

  const library = findLibrary(mod, exportName);
  if (!library) {
    const exports = Object.keys(mod).join(", ");
    console.error(
      `Error: No Library export found.\n` +
        `Found exports: ${exports || "(none)"}\n` +
        `Export a createLibrary() result, or use --export <name> to specify which export to use.`,
    );
    process.exit(1);
  }

  let output: string;
  if (jsonSchema) {
    // Output a PromptSpec-compatible JSON with component signatures, groups, and JSON schema.
    output = JSON.stringify(library.toSpec(), null, 2);
  } else {
    const promptOptions = findPromptOptions(mod, promptOptionsName);
    output = library.prompt(promptOptions);
  }

  process.stdout.write(output);
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const entryPath = args[0];
  if (!entryPath) {
    console.error(
      "Usage: generate-worker <entryPath> [exportName] " +
        "[--json-schema | --prompt-options <name> | --extension [--extension-id <id>] [--version <ver>]]",
    );
    process.exit(1);
  }

  if (args.includes("--extension")) {
    await runExtensionMode(entryPath, args);
  } else {
    await runLibraryMode(entryPath, args);
  }
}

main();

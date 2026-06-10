import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { getBuiltinsManifest } from "@openuidev/react-lang";
import { dslLibrary } from "../src/genui-lib/dslLibrary";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(scriptDir, "..");
const repoRoot = resolve(packageRoot, "../..");

const outputs = [
  resolve(packageRoot, "generated/base-contract.json"),
  resolve(repoRoot, "packages/genui-java-sdk/src/main/resources/openui/base-contract.json"),
];

// `builtins` is a global, runtime-free manifest sourced from the lang-core
// registry. It lives at the top level of the base contract (NOT inside the
// component spec) so the Java SDK can reproduce the Template/Data Built-ins
// prompt sections byte-for-byte without re-deriving them.
const contract = { ...dslLibrary.toSpec(), builtins: getBuiltinsManifest() };
const json = `${JSON.stringify(contract, null, 2)}\n`;

for (const output of outputs) {
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, json, "utf8");
  console.log(`Wrote ${output}`);
}

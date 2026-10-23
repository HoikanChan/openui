import { randomUUID } from "node:crypto";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { exportGenerationContract } from "../src/contract/export";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const canonical = resolve(
  packageRoot,
  "../genui-java-sdk/src/main/resources/openui/base-contract.json",
);
let input = canonical;
let output = canonical;
const args = process.argv.slice(2);
for (let index = 0; index < args.length; index++) {
  const option = args[index];
  if (
    (option !== "--input" && option !== "--output") ||
    !args[index + 1] ||
    args[index + 1].startsWith("--")
  ) {
    throw new Error("Usage: generate:base-contract [--input source.json] [--output contract.json]");
  }
  const path = resolve(args[++index]);
  if (option === "--input") input = path;
  else output = path;
}

// This branch retains a single canonical contract, including structured builtin
// rules not present in the historical runtime manifest. Never rebuild those rules
// from getBuiltinsManifest(), which only contains prompt documentation.
const sourceText = await readFile(input, "utf8");
const source = JSON.parse(sourceText);
const contract = exportGenerationContract(source);
const json =
  JSON.stringify(source) === JSON.stringify(contract)
    ? sourceText
    : `${JSON.stringify(contract, null, 2)}\n`;
await mkdir(dirname(output), { recursive: true });
const temporary = `${output}.${randomUUID()}.tmp`;
try {
  await writeFile(temporary, json, "utf8");
  await rename(temporary, output);
} finally {
  await rm(temporary, { force: true });
}
console.log(`Wrote ${output}`);

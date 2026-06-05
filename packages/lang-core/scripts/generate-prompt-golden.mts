// Regenerates the cross-language prompt golden files.
//
// For every `<name>.json` PromptSpec fixture under the Java SDK's
// `src/test/resources/prompt-golden/`, runs the TypeScript `generatePrompt` (the
// alignment oracle) and writes `<name>.txt` next to it. The Java `PromptGoldenTest`
// then asserts `PromptAssembler.assemble(...)` reproduces each `.txt` byte-for-byte.
//
// Run with: pnpm --dir packages/lang-core run generate:prompt-golden
import { readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { generatePrompt, type PromptSpec } from "../src/parser/prompt";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "../../..");
const goldenDir = resolve(repoRoot, "packages/genui-java-sdk/src/test/resources/prompt-golden");

const entries = await readdir(goldenDir);
const fixtures = entries.filter((file) => file.endsWith(".json")).sort();

if (fixtures.length === 0) {
  throw new Error(`No fixtures (*.json) found in ${goldenDir}`);
}

for (const fixture of fixtures) {
  const name = fixture.replace(/\.json$/, "");
  const spec = JSON.parse(await readFile(join(goldenDir, fixture), "utf8")) as PromptSpec;
  const prompt = generatePrompt(spec);
  // Write the prompt verbatim (LF, no trailing newline) so Java can assert byte equality.
  await writeFile(join(goldenDir, `${name}.txt`), prompt, "utf8");
  console.log(`Wrote ${name}.txt`);
}

import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const distDir = join(import.meta.dirname, "..", "dist");

const namespaceBlock = `
declare namespace z {
  type infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;
  type Infer<S extends Schema<SchemaDef, any>> = S extends Schema<any, infer T> ? T : never;
  type ZodType<T = any> = Schema<SchemaDef, T>;
  type ZodObject<T = any> = ObjectSchema<T>;
  type ZodTypeAny = Schema<SchemaDef, any>;
}`;

const files = ["index.d.mts", "index.d.cts"];
const errors = [];

for (const file of files) {
  const filePath = join(distDir, file);
  let content = readFileSync(filePath, "utf8");
  const original = content;

  const renameFrom = "declare const z$1:";
  const renameTo = "declare const z:";
  if (!content.includes(renameFrom)) {
    errors.push(`${file}: expected "${renameFrom}" not found — tsdown may have changed output format`);
  }
  content = content.replace(renameFrom, renameTo);

  const nsFrom = "declare namespace z {}";
  if (!content.includes(nsFrom)) {
    errors.push(`${file}: expected "${nsFrom}" not found — namespace z may not be empty after bundling`);
  }
  content = content.replace(nsFrom, namespaceBlock.trim());

  if (content === original) {
    errors.push(`${file}: no replacements made — patch is ineffective`);
  }

  writeFileSync(filePath, content, "utf8");
  console.log(`Patched ${file}`);
}

if (errors.length > 0) {
  console.error("\n❌ patch-dts robustness check FAILED:");
  for (const e of errors) console.error(`  ${e}`);
  console.error("\nThe .d.ts files may not have correct namespace z type members.");
  console.error("Check if tsdown output format has changed or namespace is no longer empty.");
  process.exit(1);
}

console.log("\n✅ patch-dts robustness check passed — all replacements verified.");

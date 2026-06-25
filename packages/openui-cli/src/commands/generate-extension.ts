import { execFileSync } from "child_process";
import * as fs from "fs";
import * as path from "path";

export interface GenerateExtensionOptions {
  out?: string;
  export?: string;
  extensionId?: string;
  version?: string;
}

/**
 * Generate a registerable Extension JSON from a complete extension object.
 *
 * The entry file exports an extension object shaped like
 * `{ extensionId?, version?, components: DefinedComponent[], componentGroups?, tools?, examples?, additionalRules? }`.
 * The worker compiles `components` (real components) into renderer-free
 * `{ description, propsSchema }` specs and passes the remaining fields through.
 * `extensionId` / `version` come from the object, optionally overridden by
 * `--extension-id` / `--version`.
 */
export async function runGenerateExtension(
  entry: string,
  options: GenerateExtensionOptions,
): Promise<void> {
  const entryPath = path.resolve(process.cwd(), entry);

  if (!fs.existsSync(entryPath)) {
    console.error(`Error: File not found: ${entryPath}`);
    process.exit(1);
  }

  const workerPath = path.join(__dirname, "generate-worker.js");

  const workerArgs = [workerPath, entryPath, "--extension"];
  if (options.export) workerArgs.push(options.export);
  if (options.extensionId) workerArgs.push("--extension-id", options.extensionId);
  if (options.version) workerArgs.push("--version", options.version);

  let output: string;
  try {
    output = execFileSync(process.execPath, workerArgs, {
      encoding: "utf-8",
      cwd: process.cwd(),
      stdio: ["inherit", "pipe", "inherit"],
    });
  } catch {
    // The worker already prints a diagnostic to stderr before exiting non-zero.
    process.exit(1);
  }

  if (options.out) {
    const outPath = path.resolve(process.cwd(), options.out);
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, output + "\n");
    console.info(`Written to ${outPath}`);
  } else {
    process.stdout.write(output + "\n");
  }
}

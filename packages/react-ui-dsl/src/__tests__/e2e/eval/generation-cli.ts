import { getBuiltinsManifest } from "@cloudsop/openui-react-lang";
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { createInterface } from "node:readline";
import { fileURLToPath } from "node:url";
import { dslLibrary } from "../../../genui-lib/dslLibrary";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../../../../../..");
const CLI_MODULE = "packages/genui-eval-cli";
const CLI_JAR = resolve(REPO_ROOT, CLI_MODULE, "target/genui-eval-cli.jar");

export const BASE_CONTRACT_FILENAME = "base-contract.json";
export const JOBS_FILENAME = "generation-jobs.json";

export interface GenerationCase {
  id: string;
  userInput: string;
  dataModel: Record<string, unknown>;
}

export interface GenerationCliResult {
  id: string;
  status: "ok" | "error";
  dsl?: string;
  error?: string;
}

/**
 * In-process export of the CURRENT dslLibrary as a base contract — always
 * fresh, never the (possibly stale) copy bundled inside the Java SDK jar.
 * Same shape as scripts/generate-base-contract.mts.
 */
export function exportBaseContract(targetPath: string): string {
  const contract = { ...dslLibrary.toSpec(), builtins: getBuiltinsManifest() };
  mkdirSync(dirname(targetPath), { recursive: true });
  writeFileSync(targetPath, `${JSON.stringify(contract, null, 2)}\n`, "utf-8");
  return contract.contractVersion;
}

/** Manifest label recording which generation pipeline produced a run. */
export function getGeneratorLabel(): string {
  return `genui-eval-cli (${dslLibrary.toSpec().contractVersion})`;
}

function assertJavaToolchain(tool: "java" | "mvn"): void {
  const probe = spawnSync(tool, ["-version"], { shell: true, encoding: "utf-8" });
  if (probe.error || probe.status !== 0) {
    throw new Error(
      `"${tool}" is not available on PATH. The eval generation pipeline runs through ` +
        `the Java Generation SDK and requires a JDK (21+) and Maven. ` +
        `Install them, then run: pnpm eval build-cli`,
    );
  }
}

/**
 * Ensure the fat jar exists. Missing jar triggers an automatic build; no
 * mtime-based staleness detection — after changing Java sources run
 * `pnpm eval build-cli` explicitly.
 */
export function ensureCliJar(options: { forceBuild?: boolean } = {}): string {
  if (existsSync(CLI_JAR) && !options.forceBuild) return CLI_JAR;

  assertJavaToolchain("java");
  assertJavaToolchain("mvn");
  console.log(`[generation-cli] Building ${CLI_MODULE} fat jar (mvn package)…`);
  const build = spawnSync("mvn", ["-q", "-pl", CLI_MODULE, "-am", "package", "-DskipTests"], {
    cwd: REPO_ROOT,
    shell: true,
    encoding: "utf-8",
  });
  if (build.status !== 0 || !existsSync(CLI_JAR)) {
    throw new Error(
      `Failed to build genui-eval-cli jar (exit ${build.status}).\n${build.stderr || build.stdout || ""}`,
    );
  }
  return CLI_JAR;
}

/**
 * Batch-generate DSL through the Eval Generation CLI (Java Generation SDK
 * path). Writes base-contract.json + jobs file into workDir, streams JSONL
 * results as they complete. The CLI itself never touches snapshot files.
 */
export async function runGenerationCli(
  cases: GenerationCase[],
  options: {
    workDir: string;
    concurrency?: number;
    onResult?: (result: GenerationCliResult) => void;
  },
): Promise<Map<string, GenerationCliResult>> {
  const jar = ensureCliJar();
  mkdirSync(options.workDir, { recursive: true });
  const contractPath = resolve(options.workDir, BASE_CONTRACT_FILENAME);
  const jobsPath = resolve(options.workDir, JOBS_FILENAME);
  exportBaseContract(contractPath);
  writeFileSync(jobsPath, JSON.stringify(cases, null, 2), "utf-8");

  const args = [
    "-jar",
    jar,
    "generate",
    `--base-contract=${contractPath}`,
    `--jobs=${jobsPath}`,
    `--concurrency=${options.concurrency ?? 6}`,
  ];

  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn("java", args, { env: process.env });
    const results = new Map<string, GenerationCliResult>();
    let stderr = "";

    child.stderr.on("data", (chunk: Buffer) => {
      stderr += chunk.toString("utf-8");
    });

    const lines = createInterface({ input: child.stdout });
    lines.on("line", (line) => {
      const trimmed = line.trim();
      if (!trimmed) return;
      try {
        const parsed = JSON.parse(trimmed) as GenerationCliResult;
        results.set(parsed.id, parsed);
        options.onResult?.(parsed);
      } catch {
        stderr += `\n[generation-cli] Unparseable output line: ${trimmed}`;
      }
    });

    child.on("error", (err) => {
      rejectPromise(new Error(`Failed to spawn java: ${err.message}`));
    });
    child.on("close", (code) => {
      if (code !== 0) {
        rejectPromise(new Error(`genui-eval-cli exited with code ${code}.\n${stderr.trim()}`));
        return;
      }
      resolvePromise(results);
    });
  });
}

/**
 * Assemble the canonical system prompt via the CLI's print-prompt command
 * (placeholder data model), using the freshly exported base contract in
 * workDir. This is the Java SDK's actual assembly — not a TS re-derivation.
 */
export function printCanonicalPrompt(workDir: string): string {
  const jar = ensureCliJar();
  mkdirSync(workDir, { recursive: true });
  const contractPath = resolve(workDir, BASE_CONTRACT_FILENAME);
  exportBaseContract(contractPath);

  const run = spawnSync("java", ["-jar", jar, "print-prompt", `--base-contract=${contractPath}`], {
    encoding: "utf-8",
    maxBuffer: 16 * 1024 * 1024,
  });
  if (run.status !== 0) {
    throw new Error(
      `genui-eval-cli print-prompt failed (exit ${run.status}).\n${run.stderr ?? ""}`,
    );
  }
  return run.stdout;
}

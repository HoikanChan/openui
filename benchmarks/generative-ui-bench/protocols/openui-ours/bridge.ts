import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../../../..");
const SDK_DIR = join(REPO_ROOT, "packages/genui-java-sdk");
const SDK_JAR = join(SDK_DIR, "target/genui-core-0.1.0-SNAPSHOT.jar");
const BRIDGE_SRC = join(__dirname, "JavaBridge.java");
const CLASSES_DIR = join(__dirname, ".classes");
const BRIDGE_CLASS = join(CLASSES_DIR, "JavaBridge.class");

function resolveFastjsonVersion(): string {
  const pomPath = join(SDK_DIR, "pom.xml");
  if (existsSync(pomPath)) {
    const pom = readFileSync(pomPath, "utf8");
    const match = pom.match(/<fastjson2\.version>([^<]+)<\/fastjson2\.version>/);
    if (match?.[1]) return match[1].trim();
  }
  return "2.0.60";
}

function resolveFastjsonJar(): string {
  const m2Repo = process.env.M2_REPO || join(process.env.HOME || "", ".m2/repository");
  const version = resolveFastjsonVersion();
  const jar = join(m2Repo, `com/alibaba/fastjson2/fastjson2/${version}/fastjson2-${version}.jar`);
  if (!existsSync(jar)) {
    throw new Error(
      `Missing fastjson2 jar at ${jar}. Run: mvn -pl packages/genui-java-sdk -am package -DskipTests`,
    );
  }
  return jar;
}

function resolveClasspath(): { compileCp: string; runCp: string } {
  if (!existsSync(SDK_JAR)) {
    throw new Error(
      `Missing ${SDK_JAR}. Build it first with: mvn -pl packages/genui-java-sdk -am package -DskipTests`,
    );
  }
  const fastjsonJar = resolveFastjsonJar();
  const compileCp = `${SDK_JAR}:${fastjsonJar}`;
  return { compileCp, runCp: `${CLASSES_DIR}:${compileCp}` };
}

function ensureCompiled(): string {
  const { compileCp, runCp } = resolveClasspath();
  const needsCompile =
    !existsSync(BRIDGE_CLASS) ||
    statSync(BRIDGE_SRC).mtimeMs > statSync(BRIDGE_CLASS).mtimeMs ||
    statSync(SDK_JAR).mtimeMs > statSync(BRIDGE_CLASS).mtimeMs;
  if (needsCompile) {
    mkdirSync(CLASSES_DIR, { recursive: true });
    execFileSync("javac", ["-cp", compileCp, "-d", CLASSES_DIR, BRIDGE_SRC], {
      stdio: "inherit",
    });
  }
  return runCp;
}

export function callJavaBridge<Req, Res>(
  command: "assemble-prompts" | "validate-batch",
  payload: Req,
): Res {
  const cp = ensureCompiled();
  const stdout = execFileSync("java", ["-cp", cp, "JavaBridge", command], {
    input: JSON.stringify(payload),
    encoding: "utf8",
    maxBuffer: 128 * 1024 * 1024,
  });
  return JSON.parse(stdout) as Res;
}

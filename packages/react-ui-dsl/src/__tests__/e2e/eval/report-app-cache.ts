import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, resolve, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { cpSync } from "node:fs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CACHE_DIR = resolve(__dirname, ".report-app-cache");

/**
 * Compute cache key based on report-app source files + key dependencies.
 * Key includes: all source files in report-app/, relevant package.json deps.
 */
export function computeReportAppCacheKey(reportAppRoot: string, packageRoot: string): string {
  const filesToHash: { path: string; content: string }[] = [];

  // Hash all source files in report-app directory
  const sourceFiles = ["index.html", "main.tsx", "styles.css"];
  for (const file of sourceFiles) {
    const filePath = resolve(reportAppRoot, file);
    if (existsSync(filePath)) {
      filesToHash.push({
        path: `report-app/${file}`,
        content: readFileSync(filePath, "utf-8"),
      });
    }
  }

  // Hash key dependency versions from package.json
  const packageJsonPath = resolve(packageRoot, "package.json");
  if (existsSync(packageJsonPath)) {
    const packageJson = JSON.parse(readFileSync(packageJsonPath, "utf-8"));
    const deps = packageJson.dependencies ?? {};
    const devDeps = packageJson.devDependencies ?? {};

    // Include key dependencies that affect report-app build
    const keyDeps = [
      "react",
      "react-dom",
      "vite",
      "@cloudsop/openui-lang-core",
      "@cloudsop/openui-react-lang",
      "@cloudsop/openui-react-ui-dsl",
    ];

    const depVersions: string[] = [];
    for (const dep of keyDeps) {
      if (deps[dep]) depVersions.push(`${dep}@${deps[dep]}`);
      if (devDeps[dep]) depVersions.push(`${dep}@${devDeps[dep]}`);
    }
    filesToHash.push({
      path: "package.json/deps",
      content: depVersions.join("\n"),
    });
  }

  // Compute combined hash
  const hasher = createHash("sha256");
  for (const file of filesToHash.sort((a, b) => a.path.localeCompare(b.path))) {
    hasher.update(`${file.path}:${file.content.length}:${file.content}`);
  }

  return hasher.digest("hex");
}

// Only cache Vite build artifacts, not run-specific data files.
const VITE_ARTIFACTS = ["index.html", "assets"];

/**
 * Restore cached build to target directory.
 * Only restores Vite build artifacts (index.html, assets/).
 * Returns true if cache exists and was restored, false otherwise.
 */
export function restoreReportAppCache(targetDir: string, key: string): boolean {
  const cachePath = resolve(CACHE_DIR, key);

  if (!existsSync(cachePath)) {
    return false;
  }

  for (const artifact of VITE_ARTIFACTS) {
    const src = resolve(cachePath, artifact);
    if (existsSync(src)) {
      cpSync(src, resolve(targetDir, artifact), { recursive: true, force: true });
    }
  }
  return true;
}

/**
 * Save Vite build artifacts to cache (index.html + assets/ only).
 * Does NOT save run-specific files like run.json or report-data.json.
 */
export function saveReportAppCache(sourceDir: string, key: string): void {
  const cachePath = resolve(CACHE_DIR, key);
  mkdirSync(cachePath, { recursive: true });

  for (const artifact of VITE_ARTIFACTS) {
    const src = resolve(sourceDir, artifact);
    if (existsSync(src)) {
      cpSync(src, resolve(cachePath, artifact), { recursive: true, force: true });
    }
  }
}

/**
 * Clear all report-app cache entries.
 */
export function clearReportAppCache(): void {
  if (existsSync(CACHE_DIR)) {
    rmSync(CACHE_DIR, { recursive: true, force: true });
  }
}
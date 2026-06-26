#!/usr/bin/env node
// Stage and (optionally) publish the eview variant as @cloudsop/dsl-engine-web.
//
// Source `package.json` is never modified. The published artifact is assembled
// inside `dist/` with its own `package.json` (renamed, no homepage/repo/bugs,
// workspace deps bundled in) and `README.md`. Publish runs from `dist/`.
//
// Usage (from packages/react-ui-dsl):
//   pnpm publish:eview                 # stage only — inspect dist/ then publish manually
//   pnpm publish:eview -- --publish    # stage + npm publish from dist/
//   pnpm publish:eview -- --publish --dry-run

import { execSync } from "node:child_process";
import { copyFileSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const PKG_ROOT = resolve(HERE, "..");
const DIST = resolve(PKG_ROOT, "dist");
const SRC_PKG = resolve(PKG_ROOT, "package.json");
const README = resolve(PKG_ROOT, "README.md");

const args = process.argv.slice(2);
const SHOULD_PUBLISH = args.includes("--publish");
const DRY_RUN = args.includes("--dry-run");

const PUBLISHED_NAME = "@cloudsop/dsl-engine-web";
const PUBLISHED_DESCRIPTION = "DSL engine for web rendering";

// peer deps in the source package.json that must NOT appear in the published
// artifact: react-lang is bundled in; antd is unreachable from the eview entry
// graph after Input/view/eview.tsx lands.
const PEERS_TO_DROP = new Set(["@cloudsop/openui-react-lang", "antd"]);

function info(msg: string): void {
  console.log(`\n[publish-eview] ${msg}`);
}

function run(cmd: string, cwd: string = PKG_ROOT): void {
  console.log(`\n→ ${cmd}${cwd !== PKG_ROOT ? ` (in ${cwd})` : ""}`);
  execSync(cmd, { stdio: "inherit", cwd });
}

interface SourcePkg {
  version: string;
  type?: string;
  sideEffects?: boolean;
  keywords?: string[];
  dependencies?: Record<string, string>;
  peerDependencies?: Record<string, string>;
  peerDependenciesMeta?: Record<string, { optional?: boolean }>;
}

function buildDistPackageJson(src: SourcePkg): Record<string, unknown> {
  const peers: Record<string, string> = {};
  for (const [name, range] of Object.entries(src.peerDependencies ?? {})) {
    if (PEERS_TO_DROP.has(name)) continue;
    peers[name] = range;
  }

  const peerMeta: Record<string, { optional?: boolean }> = {};
  for (const [name, meta] of Object.entries(src.peerDependenciesMeta ?? {})) {
    if (PEERS_TO_DROP.has(name)) continue;
    peerMeta[name] = meta;
  }

  return {
    name: PUBLISHED_NAME,
    version: src.version,
    description: PUBLISHED_DESCRIPTION,
    type: src.type ?? "module",
    main: "./index.cjs",
    module: "./index.mjs",
    types: "./index.d.cts",
    exports: {
      ".": {
        import: {
          types: "./index.d.mts",
          default: "./index.mjs",
        },
        require: {
          types: "./index.d.cts",
          default: "./index.cjs",
        },
      },
    },
    sideEffects: src.sideEffects ?? false,
    keywords: src.keywords ?? [],
    dependencies: src.dependencies ?? {},
    peerDependencies: peers,
    ...(Object.keys(peerMeta).length > 0 ? { peerDependenciesMeta: peerMeta } : {}),
  };
}

info("Cleaning dist/");
rmSync(DIST, { recursive: true, force: true });

info("Building eview variant in publish mode (REACT_UI_DSL_BUILD_MODE=publish)");
run("pnpm run build:eview:publish");

info(`Writing dist/package.json (${PUBLISHED_NAME})`);
const src = JSON.parse(readFileSync(SRC_PKG, "utf8")) as SourcePkg;
const distPkg = buildDistPackageJson(src);
writeFileSync(
  resolve(DIST, "package.json"),
  JSON.stringify(distPkg, null, 2) + "\n",
  "utf8",
);

info("Copying README.md → dist/");
copyFileSync(README, resolve(DIST, "README.md"));

if (SHOULD_PUBLISH) {
  info(`Publishing ${PUBLISHED_NAME}@${src.version} from dist/${DRY_RUN ? " (dry-run)" : ""}`);
  run(DRY_RUN ? "npm publish --dry-run" : "npm publish", DIST);
} else {
  console.log(
    `\n[publish-eview] Stage complete.\n` +
      `  - Artifact root: ${DIST}\n` +
      `  - Inspect dist/package.json, then re-run with --publish to release.\n`,
  );
}

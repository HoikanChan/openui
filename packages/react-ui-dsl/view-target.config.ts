import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Plugin } from "vite";

const CONFIG_DIR = path.dirname(fileURLToPath(import.meta.url));

const VIEW_COMPONENTS = [
  "Button",
  "Input",
  "Form",
  "Link",
  "Select",
  "Table",
  "Tabs",
  "Tag",
  "TimeLine",
] as const;

export type ReactUiDslViewTarget = "antd" | "eview";

export function getReactUiDslViewTarget(
  env: NodeJS.ProcessEnv = process.env,
): ReactUiDslViewTarget {
  return env["REACT_UI_DSL_VIEW_TARGET"] === "eview" ? "eview" : "antd";
}

export function createViewTargetAliases(
  rootDir = CONFIG_DIR,
  target = getReactUiDslViewTarget(),
): Record<string, string> {
  if (target === "antd") return {};

  return Object.fromEntries(
    VIEW_COMPONENTS.flatMap((component) => {
      const viewDir = path.resolve(rootDir, "src", "genui-lib", component, "view");
      const replacement = path.join(viewDir, "eview.tsx");
      return [
        [path.join(viewDir, "index.tsx"), replacement],
        [path.join(viewDir, "index"), replacement],
      ];
    }),
  );
}

export function shouldLoadAntdStyles(env: NodeJS.ProcessEnv = process.env): boolean {
  return getReactUiDslViewTarget(env) === "antd";
}

function normalizeIdForMatch(id: string): string {
  let normalized = id.replace(/[?#].*$/, "").replace(/\\/g, "/");
  if (process.platform === "win32") normalized = normalized.toLowerCase();
  return normalized;
}

export function reactUiDslViewTargetPlugin(
  rootDir = CONFIG_DIR,
  target = getReactUiDslViewTarget(),
): Plugin | null {
  if (target === "antd") return null;

  const lookup = new Map<string, string>();
  for (const [key, value] of Object.entries(createViewTargetAliases(rootDir, target))) {
    lookup.set(normalizeIdForMatch(key), value);
  }

  return {
    name: "react-ui-dsl-view-target",
    enforce: "pre",
    async resolveId(source, importer, options) {
      const resolved = await this.resolve(source, importer, { ...options, skipSelf: true });
      if (!resolved) return null;
      const hit = lookup.get(normalizeIdForMatch(resolved.id));
      return hit ?? null;
    },
  };
}

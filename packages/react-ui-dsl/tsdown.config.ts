import { defineConfig } from "tsdown";
import { createViewTargetAliases } from "./view-target.config.ts";

const isPublishBuild = process.env["REACT_UI_DSL_BUILD_MODE"] === "publish";

// In publish mode we inline workspace deps so the published artifact
// has no @openuidev/* runtime references. See scripts/publish-eview.mts.
const bundledWorkspacePackages = ["@cloudsop/openui-react-lang", "@cloudsop/openui-lang-core"];

const shared = {
  alias: createViewTargetAliases(),
  sourcemap: true,
  target: "es2022",
  clean: false,
  ...(isPublishBuild ? { noExternal: bundledWorkspacePackages } : {}),
} satisfies Parameters<typeof defineConfig>[0];

export default defineConfig([
  {
    ...shared,
    format: ["cjs", "esm"],
    dts: isPublishBuild
      ? { resolve: bundledWorkspacePackages }
      : true,
    entry: { index: "src/index.ts" },
  },
]);

import path from "node:path";
import { fileURLToPath } from "node:url";

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../..");
const reactPackage = path.join(workspace, "packages/react-ui-dsl/node_modules");

export default {
  root: process.env.OPENUI_VERIFY_ROOT,
  resolve: {
    alias: {
      react: path.join(reactPackage, "react"),
      "react-dom": path.join(reactPackage, "react-dom"),
      "react/jsx-runtime": path.join(reactPackage, "react/jsx-runtime.js"),
      "react/jsx-dev-runtime": path.join(reactPackage, "react/jsx-dev-runtime.js"),
      zod: path.join(reactPackage, "zod"),
      antd: path.join(reactPackage, "antd"),
      "@openuidev/lang-core": path.join(workspace, "packages/lang-core/src/index.ts"),
      "@openuidev/react-lang": path.join(workspace, "packages/react-lang/src/index.ts"),
      "@openuidev/react-ui-dsl": path.join(workspace, "packages/react-ui-dsl/src/index.ts"),
    },
  },
  server: { fs: { allow: [workspace, process.env.OPENUI_VERIFY_ROOT] } },
};

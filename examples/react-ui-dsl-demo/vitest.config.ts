import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@cloudsop/openui-lang-core": path.resolve(__dirname, "../../packages/lang-core/src/index.ts"),
      "@cloudsop/openui-react-lang": path.resolve(__dirname, "../../packages/react-lang/src/index.ts"),
      "@cloudsop/openui-react-ui-dsl": path.resolve(__dirname, "../../packages/react-ui-dsl/src/index.ts"),
    },
  },
  test: {
    environment: "node",
    exclude: ["dist/**", "node_modules/**", "e2e/**"],
  },
});

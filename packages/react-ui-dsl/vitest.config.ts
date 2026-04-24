import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@openuidev/lang-core": path.resolve(__dirname, "../lang-core/src/index.ts"),
      "@openuidev/react-lang": path.resolve(__dirname, "../react-lang/src/index.ts"),
    },
  },
  test: {
    environment: "node",
    exclude: ["dist/**", "node_modules/**", "src/__tests__/e2e/dsl-fuzz.test.tsx"],
    setupFiles: ["./src/__tests__/e2e/setup.ts"],
    testTimeout: 30000,
  },
});

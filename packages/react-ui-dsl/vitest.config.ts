import path from "node:path";
import { defineConfig } from "vitest/config";

const FUZZ_TEST_PATH = "src/__tests__/e2e/dsl-fuzz.test.tsx";

export function getTestExcludePatterns(env: NodeJS.ProcessEnv = process.env): string[] {
  const exclude = ["dist/**", "node_modules/**"];

  if (env.REACT_UI_DSL_E2E_SUITE !== "fuzz") {
    exclude.push(FUZZ_TEST_PATH);
  }

  return exclude;
}

export default defineConfig({
  resolve: {
    alias: {
      "@openuidev/lang-core": path.resolve(__dirname, "../lang-core/src/index.ts"),
      "@openuidev/react-lang": path.resolve(__dirname, "../react-lang/src/index.ts"),
    },
  },
  test: {
    environment: "node",
    exclude: getTestExcludePatterns(),
    setupFiles: ["./src/__tests__/e2e/setup.ts"],
    testTimeout: 30000,
  },
});

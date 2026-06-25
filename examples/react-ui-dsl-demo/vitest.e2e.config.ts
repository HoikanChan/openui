import path from "node:path";
import { defineConfig } from "vitest/config";

// Dedicated config for the Playwright-driven browser e2e. Kept separate from the
// unit vitest config so `pnpm test` stays fast and headless-browser-free.
export default defineConfig({
  resolve: {
    alias: {
      "@openuidev/lang-core": path.resolve(__dirname, "../../packages/lang-core/src/index.ts"),
      "@openuidev/react-lang": path.resolve(__dirname, "../../packages/react-lang/src/index.ts"),
      "@openuidev/react-ui-dsl": path.resolve(__dirname, "../../packages/react-ui-dsl/src/index.ts"),
    },
  },
  test: {
    environment: "node",
    include: ["e2e/**/*.e2e.test.ts"],
    testTimeout: 60000,
    hookTimeout: 120000,
    fileParallelism: false,
  },
});

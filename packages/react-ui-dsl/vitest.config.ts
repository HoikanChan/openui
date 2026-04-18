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
    exclude: ["dist/**", "node_modules/**"],
  },
});

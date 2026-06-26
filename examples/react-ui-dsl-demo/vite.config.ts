import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// Resolve workspace packages to TypeScript source directly — the dists are either
// not built or have broken .ts import extensions (lang-core). Vite handles TS natively.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@cloudsop/openui-lang-core": path.resolve(__dirname, "../../packages/lang-core/src/index.ts"),
      "@cloudsop/openui-react-lang": path.resolve(__dirname, "../../packages/react-lang/src/index.ts"),
      "@cloudsop/openui-react-ui-dsl": path.resolve(__dirname, "../../packages/react-ui-dsl/src/index.ts"),
    },
  },
});

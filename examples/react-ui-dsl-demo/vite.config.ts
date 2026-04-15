import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// Resolve @openuidev/react-lang to TypeScript source directly so the demo
// works without a build step (the dist is not available on Node <20.12).
// lang-core is already built so its imports resolve normally.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@openuidev/react-lang": path.resolve(__dirname, "../../packages/react-lang/src/index.ts"),
    },
  },
});

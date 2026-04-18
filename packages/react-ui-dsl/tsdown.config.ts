import { defineConfig } from "tsdown";

const shared = {
  sourcemap: true,
  target: "es2022",
  clean: false,
} satisfies Parameters<typeof defineConfig>[0];

export default defineConfig([
  {
    ...shared,
    format: ["cjs", "esm"],
    dts: true,
    entry: { index: "src/index.ts" },
  },
]);

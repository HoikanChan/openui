// Preload module: registers CSS/SCSS stub hooks so tsx can run eval-loop.ts in Node.js.
// The actual styles are irrelevant for LLM prompt generation.
import { register } from "node:module";

register(new URL("./css-stub-hooks.mjs", import.meta.url));

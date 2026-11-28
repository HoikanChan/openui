import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

describe("Stack stylesheet", () => {
  test("lets a nested Stack share its parent row instead of shrinking to content", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "stack.module.css"), "utf8");
    const rowChildBlock = stylesheet.match(/\.row\s*>\s*\.nestedStack\s*\{([^}]*)\}/s)?.[1] ?? "";

    expect(rowChildBlock).toMatch(/\bflex:\s*1 1 0\b/);
    expect(rowChildBlock).toMatch(/\bmin-width:\s*0\b/);
  });
});

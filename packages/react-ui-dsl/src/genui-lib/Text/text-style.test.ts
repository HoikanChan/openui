import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

describe("Text stylesheets", () => {
  test("defines module css classes for text sizes", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "text.module.css"), "utf8");

    expect(stylesheet).toMatch(/\.small\s*\{/);
    expect(stylesheet).toMatch(/\.default\s*\{/);
    expect(stylesheet).toMatch(/\.large\s*\{/);
    expect(stylesheet).toMatch(/\.smallHeavy\s*\{/);
    expect(stylesheet).toMatch(/\.largeHeavy\s*\{/);
  });

  test("defines module css classes for TextView rendering modes", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "view", "textView.module.css"), "utf8");

    expect(stylesheet).toMatch(/\.default\s*\{/);
    expect(stylesheet).toMatch(/\.markdown\s*\{/);
    expect(stylesheet).toMatch(/\.html\s*\{/);
  });
});

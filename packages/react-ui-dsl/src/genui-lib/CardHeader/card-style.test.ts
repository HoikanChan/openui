import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

describe("CardHeader stylesheet", () => {
  test("defines title and subtitle presentation classes", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "cardHeader.module.css"), "utf8");

    expect(stylesheet).toMatch(/\.root\s*\{/);
    expect(stylesheet).toMatch(/\.title\s*\{/);
    expect(stylesheet).toMatch(/\.subtitle\s*\{/);
  });

  test("uses the same text theme variables as react-ui CardHeader", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "cardHeader.module.css"), "utf8");

    expect(stylesheet).toContain("var(--openui-text-neutral-primary");
    expect(stylesheet).toContain("var(--openui-text-neutral-secondary");
  });
});

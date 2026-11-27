import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

describe("Card stylesheet", () => {
  test("defines a readable foreground color on the card root", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "card.module.css"), "utf8");

    expect(stylesheet).toMatch(/\.root\s*\{[^}]*\bcolor\s*:/s);
  });

  test("uses the same core theme variables as react-ui Card", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "card.module.css"), "utf8");

    expect(stylesheet).toContain("var(--openui-foreground");
    expect(stylesheet).toContain("var(--openui-sunk");
    expect(stylesheet).toContain("var(--openui-text-neutral-primary");
    expect(stylesheet).toContain("var(--openui-border-default");
  });

  test("does not force a fixed width for standard cards", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "card.module.css"), "utf8");
    const widthStandardBlock = stylesheet.match(/\.widthStandard\s*\{([^}]*)\}/s)?.[1] ?? "";

    expect(widthStandardBlock).not.toMatch(/\bwidth\s*:/);
  });
});

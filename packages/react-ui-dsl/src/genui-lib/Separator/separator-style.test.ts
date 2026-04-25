import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

describe("Separator stylesheet", () => {
  test("keeps vertical separators stretch-driven instead of percentage-height driven", () => {
    const stylesheet = fs.readFileSync(path.resolve(__dirname, "separator.module.css"), "utf8");

    expect(stylesheet).toMatch(/\.vertical\s*\{[^}]*align-self:\s*stretch/s);
    expect(stylesheet).toMatch(/\.vertical\s*\{[^}]*height:\s*auto/s);
    expect(stylesheet).toMatch(/\.vertical\s*\{[^}]*min-height:\s*1px/s);
    expect(stylesheet).not.toMatch(/\.vertical\s*\{[^}]*height:\s*100%/s);
  });
});

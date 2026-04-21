import { readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { assertSnapshotCoverage } from "./fixtureCoverage";
import { fixtures } from "./fixtures";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = resolve(__dirname, "snapshots");

function getFixtureIds(): string[] {
  return Object.values(fixtures).flatMap((group) => group.map((fixture) => fixture.id));
}

describe("react-ui-dsl e2e fixture coverage", () => {
  it("keeps a committed fixture group for every top-level DSL component", () => {
    const componentNames = Object.keys(dslLibrary.toSpec().components).filter((name) => name !== "Col");

    expect(Object.keys(fixtures).sort()).toEqual(componentNames.sort());
  });

  it("keeps fixture assert payload small and uses function assertions for rare cases", () => {
    const allowedKeys = ["contains", "notContains", "verify"];

    for (const fixture of Object.values(fixtures).flat()) {
      expect(
        Object.keys(fixture.assert).sort(),
        `${fixture.id}: fixture.assert should only use ${allowedKeys.join(", ")}`,
      ).toEqual(expect.arrayContaining(Object.keys(fixture.assert).sort()));
      expect(
        Object.keys(fixture.assert).every((key) => allowedKeys.includes(key)),
        `${fixture.id}: move uncommon assert keys to assert.verify`,
      ).toBe(true);
    }
  });

  it("keeps committed DSL snapshots aligned with fixture ids", () => {
    const fixtureIds = getFixtureIds().sort();
    const snapshotIds = readdirSync(SNAPSHOT_DIR)
      .filter((fileName) => fileName.endsWith(".dsl"))
      .map((fileName) => fileName.replace(/\.dsl$/, ""))
      .sort();

    assertSnapshotCoverage({ fixtureIds, snapshotIds });
  });
});

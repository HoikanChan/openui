import { describe, expect, it } from "vitest";
import { assertSnapshotCoverage } from "./fixtureCoverage";

describe("assertSnapshotCoverage", () => {
  it("throws a regen-focused error when fixture ids and snapshots diverge", () => {
    expect(() =>
      assertSnapshotCoverage({
        fixtureIds: ["button-primary", "chart-latency"],
        snapshotIds: ["button-primary", "old-snapshot"],
      }),
    ).toThrowErrorMatchingInlineSnapshot(`
      [Error: DSL snapshot coverage is out of date.

      This is a fixture/snapshot sync error, not a rendering logic failure.
      This usually means fixture ids changed and committed snapshots were not regenerated.
      Run \`pnpm test:e2e:regen\` in \`packages/react-ui-dsl\` to refresh \`src/__tests__/e2e/snapshots\`.

      Missing snapshots:
      - chart-latency

      Orphaned snapshots:
      - old-snapshot]
    `);
  });

  it("does nothing when fixture ids and snapshots are aligned", () => {
    expect(() =>
      assertSnapshotCoverage({
        fixtureIds: ["button-primary", "chart-latency"],
        snapshotIds: ["button-primary", "chart-latency"],
      }),
    ).not.toThrow();
  });
});

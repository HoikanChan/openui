export function assertSnapshotCoverage(input: {
  fixtureIds: string[];
  snapshotIds: string[];
}): void {
  const fixtureIds = [...input.fixtureIds].sort();
  const snapshotIds = [...input.snapshotIds].sort();
  const fixtureIdSet = new Set(fixtureIds);
  const snapshotIdSet = new Set(snapshotIds);

  const missingSnapshots = fixtureIds.filter((id) => !snapshotIdSet.has(id));
  const orphanedSnapshots = snapshotIds.filter((id) => !fixtureIdSet.has(id));

  if (missingSnapshots.length === 0 && orphanedSnapshots.length === 0) {
    return;
  }

  const sections = [
    "DSL snapshot coverage is out of date.",
    "",
    "This is a fixture/snapshot sync error, not a rendering logic failure.",
    "This usually means fixture ids changed and committed snapshots were not regenerated.",
    "Run `pnpm test:e2e:regen` in `packages/react-ui-dsl` to refresh `src/__tests__/e2e/snapshots`.",
  ];

  if (missingSnapshots.length > 0) {
    sections.push("", "Missing snapshots:", ...missingSnapshots.map((id) => `- ${id}`));
  }

  if (orphanedSnapshots.length > 0) {
    sections.push("", "Orphaned snapshots:", ...orphanedSnapshots.map((id) => `- ${id}`));
  }

  throw new Error(sections.join("\n"));
}

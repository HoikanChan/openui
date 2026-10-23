import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { afterEach, expect, it } from "vitest";

const directories: string[] = [];
afterEach(() =>
  directories.splice(0).forEach((directory) => rmSync(directory, { recursive: true, force: true })),
);
function fixture(source: object) {
  const directory = mkdtempSync(resolve(tmpdir(), "contract-export-"));
  directories.push(directory);
  const input = resolve(directory, "source.json");
  const output = resolve(directory, "contract.json");
  writeFileSync(input, JSON.stringify(source));
  return { input, output };
}
const cli = resolve("scripts/generate-base-contract.mts");

it("runs the public CLI and writes the complete validated contract", () => {
  const source = JSON.parse(
    readFileSync(resolve("../genui-java-sdk/src/main/resources/openui/base-contract.json"), "utf8"),
  );
  delete source.capabilityGaps;
  const { input, output } = fixture(source);
  execFileSync(process.execPath, ["--import", "tsx", cli, "--input", input, "--output", output]);
  const exported = JSON.parse(readFileSync(output, "utf8"));
  expect(exported.capabilityGaps.map((gap: { id: string }) => gap.id)).toEqual([
    "group-by",
    "top-n",
    "join",
  ]);
  delete exported.capabilityGaps;
  expect(exported).toEqual(source);
});

it.each([
  ["GroupBy", { GroupBy: {} }, []],
  [" @Table ", { Table: {} }, []],
  [" @Join ", {}, [{ name: "Join" }]],
])("fails before replacing an artifact on alias conflict %s", (alias, components, builtins) => {
  const { input, output } = fixture({
    contractVersion: "v1",
    components,
    builtins,
    capabilityGaps: [
      {
        id: "custom",
        kind: "component",
        name: "Gap",
        aliases: [alias],
        phrases: [],
        fallback: "Show Table",
      },
    ],
  });
  writeFileSync(output, "previous artifact");
  const result = spawnSync(
    process.execPath,
    ["--import", "tsx", cli, "--input", input, "--output", output],
    { encoding: "utf8" },
  );
  expect(result.status).not.toBe(0);
  expect(result.stderr).toMatch(/alias.*conflicts/);
  expect(readFileSync(output, "utf8")).toBe("previous artifact");
});

it("retains an already exported canonical artifact byte for byte", () => {
  const sourceText = readFileSync(
    resolve("../genui-java-sdk/src/main/resources/openui/base-contract.json"),
    "utf8",
  );
  const { input, output } = fixture(JSON.parse(sourceText));
  writeFileSync(input, sourceText);
  execFileSync(process.execPath, ["--import", "tsx", cli, "--input", input, "--output", output]);
  expect(readFileSync(output, "utf8")).toBe(sourceText);
});

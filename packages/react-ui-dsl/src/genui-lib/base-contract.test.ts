import { getBuiltinsManifest } from "@openuidev/react-lang";
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { DSL_BASE_CONTRACT_VERSION, dslComponentGroups, dslLibrary } from "./dslLibrary";
import { PACKAGE_NAME, PACKAGE_VERSION } from "./packageMetadata";

describe("DSLEngine base contract", () => {
  it("exports model-visible component contract data", () => {
    const contract = dslLibrary.toSpec();

    expect(contract.contractVersion).toBe(DSL_BASE_CONTRACT_VERSION);
    expect(contract.root).toBe("Stack");
    expect(contract.components.Stack.signature).toContain("Stack(");
    expect(contract.componentGroups?.map((group) => group.name)).toEqual(
      dslComponentGroups.map((group) => group.name),
    );
    expect(contract.additionalRules?.length).toBeGreaterThan(0);
    expect(contract.examples?.length).toBeGreaterThan(0);
    expect(Object.keys(contract.components.Stack).sort()).toEqual(["description", "signature"]);
  });

  it("ships a top-level ordered builtins manifest for the Java SDK", () => {
    // The generated base-contract.json adds `builtins` alongside toSpec() output.
    const baseContract = { ...dslLibrary.toSpec(), builtins: getBuiltinsManifest() };

    expect(baseContract.builtins.length).toBeGreaterThan(0);
    for (const entry of baseContract.builtins) {
      expect(Object.keys(entry).sort()).toEqual(["description", "signature", "templateBuiltin"]);
    }

    // Template builtins render in a fixed order: Switch → Each → Render.
    const templates = baseContract.builtins
      .filter((entry) => entry.templateBuiltin)
      .map((entry) => entry.signature);
    expect(templates[0]).toContain("Switch(");
    expect(templates[1]).toContain("Each(");
    expect(templates[2]).toContain("Render(");

    // Data builtins (e.g. Count) are non-template and come from the same registry.
    expect(
      baseContract.builtins.some((entry) => !entry.templateBuiltin && entry.signature.startsWith("Count(")),
    ).toBe(true);
  });

  it("derives the base contract version from package metadata", () => {
    const packageJson = JSON.parse(
      readFileSync(new URL("../../package.json", import.meta.url), "utf8"),
    ) as { name: string; version: string };

    expect(PACKAGE_NAME).toBe(packageJson.name);
    expect(PACKAGE_VERSION).toBe(packageJson.version);
    expect(DSL_BASE_CONTRACT_VERSION).toBe(`${packageJson.name}@${packageJson.version}`);
  });
});

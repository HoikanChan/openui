import { describe, expect, it } from "vitest";
import { BUILTINS, LAZY_BUILTIN_DEFS, getBuiltinsManifest } from "../parser/builtins";

describe("getBuiltinsManifest", () => {
  it("emits entries in [BUILTINS, LAZY_BUILTIN_DEFS] order", () => {
    const expected = [...Object.values(BUILTINS), ...Object.values(LAZY_BUILTIN_DEFS)].map(
      (b) => b.signature,
    );
    expect(getBuiltinsManifest().map((entry) => entry.signature)).toEqual(expected);
  });

  it("drops the runtime fn and keeps only doc fields", () => {
    for (const entry of getBuiltinsManifest()) {
      expect(Object.keys(entry).sort()).toEqual(["description", "signature", "templateBuiltin"]);
      expect(typeof entry.templateBuiltin).toBe("boolean");
    }
  });

  it("attributes Switch/Each/Render as the ordered template builtins", () => {
    const templates = getBuiltinsManifest()
      .filter((entry) => entry.templateBuiltin)
      .map((entry) => entry.signature);
    expect(templates).toEqual([
      BUILTINS.Switch.signature,
      LAZY_BUILTIN_DEFS.Each.signature,
      LAZY_BUILTIN_DEFS.Render.signature,
    ]);
  });

  it("classifies data builtins (e.g. Count) as non-template", () => {
    const count = getBuiltinsManifest().find((entry) => entry.signature.startsWith("Count("));
    expect(count?.templateBuiltin).toBe(false);
  });
});

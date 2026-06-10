import { dslLibrary } from "@openuidev/react-ui-dsl";
import { describe, expect, it } from "vitest";
import { libraryForContext } from "./extensions";

describe("libraryForContext", () => {
  it("extends the base library with AlarmBadge for noe-biz-components", () => {
    const lib = libraryForContext("noe-biz-components");
    expect(Object.keys(lib.components)).toContain("AlarmBadge");
    expect(Object.keys(lib.toJSONSchema().$defs ?? {})).toContain("AlarmBadge");
  });

  it("does not mutate the base library (immutable extend)", () => {
    libraryForContext("noe-biz-components");
    expect(Object.keys(dslLibrary.components)).not.toContain("AlarmBadge");
  });

  it("falls back to the base library for unknown or empty contexts", () => {
    expect(libraryForContext(undefined)).toBe(dslLibrary);
    expect(libraryForContext("noe-alarm-tools")).toBe(dslLibrary);
  });
});

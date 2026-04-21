import { describe, expect, it } from "vitest";
import { BUILTINS } from "../parser/builtins";

const switchFn = BUILTINS.Switch.fn;

describe("Switch builtin", () => {
  it("matches integer enum via string coercion", () => {
    expect(switchFn(1, { "0": "Pending", "1": "Active", "2": "Inactive" })).toBe("Active");
  });

  it("matches string enum", () => {
    expect(switchFn("admin", { admin: "管理员", user: "用户" })).toBe("管理员");
  });

  it("returns explicit default when no case matches", () => {
    expect(switchFn(99, { "0": "A", "1": "B" }, "Unknown")).toBe("Unknown");
  });

  it("returns null when no case matches and no default", () => {
    expect(switchFn(99, { "0": "A" })).toBe(null);
  });

  it("coerces null value to empty string key", () => {
    expect(switchFn(null, { "": "empty", "0": "zero" }, "fallback")).toBe("empty");
  });

  it("returns default when null value has no matching key", () => {
    expect(switchFn(null, { "0": "zero" }, "fallback")).toBe("fallback");
  });

  it("returns default when cases is null", () => {
    expect(switchFn(1, null, "fallback")).toBe("fallback");
  });

  it("returns null default when cases is null and no default provided", () => {
    expect(switchFn(1, null)).toBe(null);
  });

  it("composes correctly — Each would iterate results", () => {
    const rows = [{ status: 0 }, { status: 1 }];
    const cases = { "0": "Pending", "1": "Active" };
    const results = rows.map((row) => switchFn(row.status, cases));
    expect(results).toEqual(["Pending", "Active"]);
  });

  it("returns matched ElementNode when case values are components", () => {
    const badge = { type: "element", typeName: "Badge", props: { color: "green" } };
    const label = { type: "element", typeName: "Label", props: { color: "red" } };
    const cases = { active: badge, inactive: label };
    expect(switchFn("active", cases)).toBe(badge);
    expect(switchFn("inactive", cases)).toBe(label);
  });

  it("returns default component when no case matches", () => {
    const fallback = { type: "element", typeName: "Badge", props: { color: "gray" } };
    expect(switchFn("unknown", { active: {} }, fallback)).toBe(fallback);
  });
});

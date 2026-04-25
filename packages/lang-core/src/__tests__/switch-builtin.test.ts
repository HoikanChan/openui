import { describe, expect, it } from "vitest";
import { BUILTINS } from "../parser/builtins";

const switchFn = BUILTINS.Switch.fn;
const runtime = {};

describe("Switch builtin", () => {
  it("matches integer enum via string coercion", () => {
    expect(switchFn(runtime, 1, { "0": "Pending", "1": "Active", "2": "Inactive" })).toBe(
      "Active",
    );
  });

  it("matches string enum", () => {
    expect(switchFn(runtime, "admin", { admin: "Admin", user: "User" })).toBe("Admin");
  });

  it("returns explicit default when no case matches", () => {
    expect(switchFn(runtime, 99, { "0": "A", "1": "B" }, "Unknown")).toBe("Unknown");
  });

  it("returns null when no case matches and no default", () => {
    expect(switchFn(runtime, 99, { "0": "A" })).toBe(null);
  });

  it("coerces null value to empty string key", () => {
    expect(switchFn(runtime, null, { "": "empty", "0": "zero" }, "fallback")).toBe("empty");
  });

  it("returns default when null value has no matching key", () => {
    expect(switchFn(runtime, null, { "0": "zero" }, "fallback")).toBe("fallback");
  });

  it("returns default when cases is null", () => {
    expect(switchFn(runtime, 1, null, "fallback")).toBe("fallback");
  });

  it("returns null default when cases is null and no default provided", () => {
    expect(switchFn(runtime, 1, null)).toBe(null);
  });

  it("composes correctly when mapping rows", () => {
    const rows = [{ status: 0 }, { status: 1 }];
    const cases = { "0": "Pending", "1": "Active" };
    const results = rows.map((row) => switchFn(runtime, row.status, cases));
    expect(results).toEqual(["Pending", "Active"]);
  });

  it("returns matched ElementNode when case values are components", () => {
    const badge = { type: "element", typeName: "Badge", props: { color: "green" } };
    const label = { type: "element", typeName: "Label", props: { color: "red" } };
    const cases = { active: badge, inactive: label };
    expect(switchFn(runtime, "active", cases)).toBe(badge);
    expect(switchFn(runtime, "inactive", cases)).toBe(label);
  });

  it("returns default component when no case matches", () => {
    const fallback = { type: "element", typeName: "Badge", props: { color: "gray" } };
    expect(switchFn(runtime, "unknown", { active: {} }, fallback)).toBe(fallback);
  });
});

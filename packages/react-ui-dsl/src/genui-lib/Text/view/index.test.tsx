import { describe, expect, it } from "vitest";
import { TextView } from ".";

describe("TextView", () => {
  it("accepts the legacy content prop for default text rendering", () => {
    const rendered = TextView({ content: "Release notes" });

    expect(rendered.type).toBe("span");
    expect(rendered.props.children).toBe("Release notes");
  });
});

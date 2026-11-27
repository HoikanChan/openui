// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TextAreaView } from "./view";

describe("TextArea", () => {
  it("renders the value, placeholder and row count", () => {
    const { container } = render(
      <TextAreaView placeholder="Describe the issue" rows={5} value="Existing text" />,
    );

    const textarea = container.querySelector("textarea");

    expect(textarea?.getAttribute("placeholder")).toBe("Describe the issue");
    expect(textarea?.getAttribute("rows")).toBe("5");
    expect(textarea?.value).toBe("Existing text");
  });
});

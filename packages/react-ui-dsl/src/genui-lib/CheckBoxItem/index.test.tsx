// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CheckBoxItemView } from "./view";

describe("CheckBoxItem", () => {
  it("renders its label, description and initial checked state", () => {
    const { container } = render(
      <CheckBoxItemView defaultChecked description="Only once" label="Notify me" name="notify" />,
    );

    expect(container.textContent).toContain("Notify me");
    expect(container.textContent).toContain("Only once");
    expect(container.querySelector("input")?.checked).toBe(true);
  });
});

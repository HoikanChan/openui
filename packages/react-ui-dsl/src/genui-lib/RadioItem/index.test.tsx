// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RadioItemView } from "./view";

describe("RadioItem", () => {
  it("renders its label and description", () => {
    const { container } = render(
      <RadioItemView description="Ship it now" label="Express" value="express" />,
    );

    expect(container.textContent).toContain("Express");
    expect(container.textContent).toContain("Ship it now");
  });
});

// @vitest-environment jsdom
import React from "react";
import { createLibrary } from "@cloudsop/openui-react-lang";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "../Button";
import { Buttons } from "./index";
import { ButtonsView } from "./view";

describe("Buttons", () => {
  it("exposes a Button element array to the contract", () => {
    const library = createLibrary({ root: "Buttons", components: [Buttons, Button] });

    expect(library.toSpec().components.Buttons.propsSchema?.properties.buttons).toEqual({
      type: "array",
      items: { component: "Button" },
    });
  });

  it("renders its button children", () => {
    const { container } = render(
      <ButtonsView direction="row">
        <span>Save</span>
        <span>Cancel</span>
      </ButtonsView>,
    );

    expect(container.textContent).toContain("Save");
    expect(container.textContent).toContain("Cancel");
  });
});

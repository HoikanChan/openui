// @vitest-environment jsdom
import React from "react";
import { createLibrary } from "@cloudsop/openui-react-lang";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FormControl } from "./index";
import { FormControlView } from "./view";

describe("FormControl", () => {
  it("exposes every contract input component as an anyOf option", () => {
    const library = createLibrary({ root: "FormControl", components: [FormControl] });

    expect(library.toSpec().components.FormControl.propsSchema?.properties.input).toEqual({
      anyOf: [
        { component: "Input" },
        { component: "TextArea" },
        { component: "Select" },
        { component: "Slider" },
        { component: "CheckBoxGroup" },
        { component: "RadioGroup" },
        { component: "DatePicker" },
      ],
    });
  });

  it("renders the label, input, hint and required marker", () => {
    const { container } = render(
      <FormControlView
        hint="We will never share it"
        input={<span>control</span>}
        label="Email"
        name="email"
        required
      />,
    );

    expect(container.textContent).toContain("Email");
    expect(container.textContent).toContain("control");
    expect(container.textContent).toContain("We will never share it");
    expect(container.textContent).toContain("*");
  });
});

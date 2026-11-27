// @vitest-environment jsdom
import React from "react";
import { createLibrary } from "@cloudsop/openui-react-lang";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RadioItem } from "../RadioItem";
import { RadioGroup } from "./index";

const items = [
  {
    type: "element" as const,
    typeName: "RadioItem",
    partial: false,
    props: { label: "Standard", description: "3-5 days", value: "standard" },
  },
  {
    type: "element" as const,
    typeName: "RadioItem",
    partial: false,
    props: { label: "Express", description: "1 day", value: "express" },
  },
];

describe("RadioGroup", () => {
  it("exposes a RadioItem element array to the contract", () => {
    const library = createLibrary({ root: "RadioGroup", components: [RadioGroup, RadioItem] });

    expect(library.toSpec().components.RadioGroup.propsSchema?.properties.items).toEqual({
      type: "array",
      items: { component: "RadioItem" },
    });
  });

  it("renders each child item and applies the default selection", () => {
    const element = RadioGroup.component({
      props: { items, defaultValue: "express" },
      renderNode: () => null,
    });

    const { container } = render(<>{element}</>);

    expect(container.textContent).toContain("Standard");
    expect(container.textContent).toContain("1 day");

    const radios = container.querySelectorAll("input[type=radio]");
    expect(radios[0]?.checked).toBe(false);
    expect(radios[1]?.checked).toBe(true);
  });
});

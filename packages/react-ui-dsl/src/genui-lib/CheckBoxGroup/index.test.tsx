// @vitest-environment jsdom
import React from "react";
import { createLibrary } from "@cloudsop/openui-react-lang";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CheckBoxItem } from "../CheckBoxItem";
import { CheckBoxGroup } from "./index";

const items = [
  {
    type: "element" as const,
    typeName: "CheckBoxItem",
    partial: false,
    props: { name: "email", label: "Email", description: "Daily digest", defaultChecked: false },
  },
  {
    type: "element" as const,
    typeName: "CheckBoxItem",
    partial: false,
    props: { name: "sms", label: "SMS" },
  },
];

describe("CheckBoxGroup", () => {
  it("exposes a CheckBoxItem element array to the contract", () => {
    const library = createLibrary({
      root: "CheckBoxGroup",
      components: [CheckBoxGroup, CheckBoxItem],
    });

    expect(library.toSpec().components.CheckBoxGroup.propsSchema?.properties.items).toEqual({
      type: "array",
      items: { component: "CheckBoxItem" },
    });
  });

  it("renders each child item and applies value defaults", () => {
    const element = CheckBoxGroup.component({
      props: { items, value: { sms: true } },
      renderNode: () => null,
    });

    const { container } = render(<>{element}</>);

    expect(container.textContent).toContain("Email");
    expect(container.textContent).toContain("Daily digest");
    expect(container.textContent).toContain("SMS");

    const checkboxes = container.querySelectorAll("input[type=checkbox]");
    expect(checkboxes[0]?.checked).toBe(false);
    expect(checkboxes[1]?.checked).toBe(true);
  });
});

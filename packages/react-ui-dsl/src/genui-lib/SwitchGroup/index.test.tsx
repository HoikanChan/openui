// @vitest-environment jsdom
import React from "react";
import { createLibrary } from "@cloudsop/openui-react-lang";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SwitchItem } from "../SwitchItem";
import { SwitchGroup } from "./index";

const items = [
  {
    type: "element" as const,
    typeName: "SwitchItem",
    partial: false,
    props: { name: "email", label: "Email alerts", defaultChecked: true },
  },
  {
    type: "element" as const,
    typeName: "SwitchItem",
    partial: false,
    props: { name: "sms", label: "SMS alerts" },
  },
];

describe("SwitchGroup", () => {
  it("exposes a SwitchItem element array to the contract", () => {
    const library = createLibrary({ root: "SwitchGroup", components: [SwitchGroup, SwitchItem] });

    expect(library.toSpec().components.SwitchGroup.propsSchema?.properties.items).toEqual({
      type: "array",
      items: { component: "SwitchItem" },
    });
  });

  it("renders each child item and applies value defaults", () => {
    const element = SwitchGroup.component({
      props: { items, value: { email: false, sms: true } },
      renderNode: () => null,
    });

    const { container } = render(<>{element}</>);

    expect(container.textContent).toContain("Email alerts");
    expect(container.textContent).toContain("SMS alerts");

    const switches = container.querySelectorAll('[role="switch"]');
    expect(switches[0]?.getAttribute("aria-checked")).toBe("false");
    expect(switches[1]?.getAttribute("aria-checked")).toBe("true");
  });
});

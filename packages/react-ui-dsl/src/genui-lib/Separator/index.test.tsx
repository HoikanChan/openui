// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { Renderer } from "@openuidev/react-lang";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";

describe("Separator DSL component", () => {
  it("renders the shared separator class by default", () => {
    const dsl = `root = VLayout([rule])
rule = Separator()`;

    const { container } = render(<Renderer library={dslLibrary} response={dsl} dataModel={{}} />);

    expect(container.querySelector(".openui-separator")).not.toBeNull();
  });

  it("passes vertical orientation to the rendered separator", () => {
    const dsl = `root = VLayout([rule])
rule = Separator("vertical")`;

    const { container } = render(<Renderer library={dslLibrary} response={dsl} dataModel={{}} />);

    expect(container.querySelector('.openui-separator[data-orientation="vertical"]')).not.toBeNull();
  });
});

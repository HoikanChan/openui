import React from "react";
import { describe, expect, it } from "vitest";
import { MarkDownRenderer } from ".";
import { MarkdownView } from "../../components/MarkdownView";
import { dslLibrary } from "../dslLibrary";

describe("MarkDownRenderer renderer", () => {
  it("renders a <div> as root element", () => {
    const rendered = MarkDownRenderer.component({ props: { textMarkdown: "# Hello" } });
    expect(rendered.type).toBe("div");
  });

  it("applies a CSS className when using default clear variant", () => {
    const rendered = MarkDownRenderer.component({ props: { textMarkdown: "# Hello" } });
    expect(rendered.props.className).toBeTruthy();
  });

  it("applies a CSS className for each variant", () => {
    for (const variant of ["clear", "card", "sunk"] as const) {
      const rendered = MarkDownRenderer.component({ props: { textMarkdown: "text", variant } });
      expect(rendered.props.className).toBeTruthy();
    }
  });

  it("uses MarkdownView as its child renderer", () => {
    const rendered = MarkDownRenderer.component({ props: { textMarkdown: "**doc**", variant: "card" } });
    const child = React.Children.only(rendered.props.children) as React.ReactElement;
    expect(child.type).toBe(MarkdownView);
    expect(child.props).toMatchObject({ content: "**doc**" });
  });

  it("passes textMarkdown prop as content to MarkdownView", () => {
    const rendered = MarkDownRenderer.component({ props: { textMarkdown: "# Title" } });
    const child = React.Children.only(rendered.props.children) as React.ReactElement;
    expect(child.props.content).toBe("# Title");
  });

  it("MarkDownRenderer is in the DSL registry", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components).toHaveProperty("MarkDownRenderer");
  });
});

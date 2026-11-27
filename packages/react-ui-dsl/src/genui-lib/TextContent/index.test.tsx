import React from "react";
import { describe, expect, it } from "vitest";
import { TextContent } from ".";
import { MarkdownView } from "../../components/MarkdownView";
import { dslLibrary } from "../dslLibrary";

describe("TextContent renderer", () => {
  it("renders a <div> as root element (not span)", () => {
    const rendered = TextContent.component({ props: { text: "Hello", size: "large-heavy" } });
    expect(rendered.type).toBe("div");
  });

  it("applies a CSS className for large-heavy size", () => {
    const rendered = TextContent.component({ props: { text: "Title", size: "large-heavy" } });
    expect(rendered.props.className).toBeTruthy();
  });

  it("applies a CSS className for each size variant", () => {
    for (const size of ["small", "default", "large", "small-heavy", "large-heavy"] as const) {
      const rendered = TextContent.component({ props: { text: "text", size } });
      expect(rendered.props.className).toBeTruthy();
    }
  });

  it("uses MarkdownView as its child renderer", () => {
    const rendered = TextContent.component({ props: { text: "**bold**" } });
    const child = React.Children.only(rendered.props.children) as React.ReactElement;
    expect(child.type).toBe(MarkdownView);
    expect(child.props).toMatchObject({ content: "**bold**" });
  });

  it("passes text prop as content to MarkdownView", () => {
    const rendered = TextContent.component({ props: { text: "Release notes" } });
    const child = React.Children.only(rendered.props.children) as React.ReactElement;
    expect(child.props.content).toBe("Release notes");
  });

  it("Text component is not in the DSL registry", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components).not.toHaveProperty("Text");
  });

  it("TextContent is in the DSL registry", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components).toHaveProperty("TextContent");
  });
});

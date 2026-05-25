import React from "react";
import { describe, expect, it } from "vitest";
import { MarkdownView } from ".";
import ReactMarkdown from "react-markdown";

describe("MarkdownView", () => {
  it("renders a <div> as root element", () => {
    const result = MarkdownView({ content: "# Title" });
    expect(result.type).toBe("div");
  });

  it("applies a CSS className to the root div", () => {
    const result = MarkdownView({ content: "hello" });
    expect(result.props.className).toBeTruthy();
  });

  it("passes content to ReactMarkdown as children", () => {
    const result = MarkdownView({ content: "**bold**" });
    const child = React.Children.only(result.props.children) as React.ReactElement;
    expect(child.type).toBe(ReactMarkdown);
    expect(child.props).toMatchObject({ children: "**bold**" });
  });

  it("renders with empty string content", () => {
    const result = MarkdownView({ content: "" });
    expect(result.type).toBe("div");
    const child = React.Children.only(result.props.children) as React.ReactElement;
    expect(child.type).toBe(ReactMarkdown);
    expect(child.props.children).toBe("");
  });
});

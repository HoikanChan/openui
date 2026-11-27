import React from "react";
import { describe, expect, test } from "vitest";
import meta from "./index.stories";

describe("Card story", () => {
  test("keeps only variant controls in args and composes header content through children", () => {
    if (!meta.render || !meta.args) {
      throw new Error("Card story meta must define render and args");
    }

    const rendered = meta.render(meta.args, {} as never);
    const children = React.Children.toArray(rendered.props.children);

    expect(meta.args).toEqual({
      variant: "card",
    });
    expect(meta.args).not.toHaveProperty("header");
    expect(meta.args).not.toHaveProperty("width");
    expect(children).toHaveLength(2);
  });
});

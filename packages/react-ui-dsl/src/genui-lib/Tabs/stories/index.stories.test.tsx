import React from "react";
import { describe, expect, test } from "vitest";
import meta, { Loading } from "./index.stories";

describe("Tabs story", () => {
  test("Default story renders two loaded tabs", () => {
    if (!meta.render || !meta.args) {
      throw new Error("Tabs story meta must define render and args");
    }
    const rendered = meta.render(meta.args, {} as never);
    expect(rendered.props.items).toHaveLength(2);
    expect(rendered.props.items[0].loading).toBe(false);
    expect(rendered.props.items[1].loading).toBe(false);
  });

  test("Loading story has one loading tab", () => {
    const args = { ...meta.args, ...Loading.args };
    const rendered = meta.render!(args as typeof meta.args, {} as never);
    expect(rendered.props.items[1].loading).toBe(true);
  });
});

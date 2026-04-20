import React from "react";
import { describe, expect, test } from "vitest";
import meta from "./index.stories";

describe("Card story", () => {
  test("keeps header args serializable for Storybook controls", () => {
    if (!meta.render || !meta.args) {
      throw new Error("Card story meta must define render and args");
    }

    const rendered = meta.render(meta.args, {} as never);

    expect(rendered.props.header).toEqual({
      subtitle: "Deployment health",
      title: "Runtime rollout",
    });
    expect(rendered.props.headerActions).toBeTruthy();
  });
});

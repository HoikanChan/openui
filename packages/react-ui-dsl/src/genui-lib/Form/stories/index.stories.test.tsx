import React from "react";
import { describe, expect, test } from "vitest";
import meta from "./index.stories";

describe("Form story", () => {
  test("keeps Storybook args serializable by hiding concrete field components behind a wrapper", () => {
    if (!meta.component || !meta.args) {
      throw new Error("Form story meta must define component and args");
    }

    const rendered = React.createElement(meta.component, meta.args);

    expect(rendered.props).not.toHaveProperty("fields");
    expect(rendered.props.layout).toBe("vertical");
    expect(rendered.props.labelAlign).toBe("left");
  });
});

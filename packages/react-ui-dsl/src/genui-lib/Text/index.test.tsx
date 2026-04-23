import React from "react";
import { describe, expect, it } from "vitest";
import { Text } from ".";
import { TextView } from "./view";

describe("Text renderer", () => {
  it("maps text props into the legacy TextView contract", () => {
    const rendered = Text.component({
      props: {
        size: "large-heavy",
        text: "Release notes",
      },
    });

    expect(rendered.type).toBe("span");
    expect(rendered.props.className).toBeTruthy();

    const child = React.Children.only(rendered.props.children) as React.ReactElement;

    expect(child.type).toBe(TextView);
    expect(child.props).toMatchObject({
      content: "Release notes",
    });
  });
});

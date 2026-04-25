import React from "react";
import { describe, expect, it } from "vitest";
import { Card } from "..";

describe("Card renderer", () => {
  it("defaults to card variant and standard width", () => {
    const rendered = Card.component({
      props: {
        children: ["Body copy"],
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.props.variant).toBe("card");
    expect(rendered.props.width).toBe("standard");
    expect(rendered.props.children).toEqual(["Body copy"]);
  });

  it("passes variant and width through to CardView", () => {
    const rendered = Card.component({
      props: {
        children: ["Body copy"],
        variant: "sunk",
        width: "full",
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.props.variant).toBe("sunk");
    expect(rendered.props.width).toBe("full");
  });
});

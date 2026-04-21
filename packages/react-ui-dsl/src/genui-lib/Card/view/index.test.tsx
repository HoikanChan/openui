import React from "react";
import { describe, expect, it } from "vitest";
import { Card } from "..";

describe("Card renderer", () => {
  it("uses full-width card defaults when no layout props are provided", () => {
    const rendered = Card.component({
      props: {
        children: ["Body copy"],
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.props.variant).toBe("card");
    expect(rendered.props.style).toMatchObject({
      flexDirection: "column",
      flexWrap: "nowrap",
      gap: "var(--openui-space-m, 12px)",
    });
    expect(rendered.props.children).toEqual(["Body copy"]);
  });

  it("maps variant and flex props into the view style contract", () => {
    const rendered = Card.component({
      props: {
        align: "center",
        children: ["Body copy"],
        direction: "row",
        gap: "l",
        justify: "between",
        variant: "sunk",
        wrap: true,
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.props.variant).toBe("sunk");
    expect(rendered.props.style).toMatchObject({
      alignItems: "center",
      flexDirection: "row",
      flexWrap: "wrap",
      gap: "var(--openui-space-l, 16px)",
      justifyContent: "space-between",
    });
  });
});

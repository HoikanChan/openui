import React from "react";
import { describe, expect, it } from "vitest";
import { CardHeaderView } from ".";

describe("CardHeaderView", () => {
  it("returns null when both title and subtitle are missing", () => {
    expect(CardHeaderView({})).toBeNull();
  });

  it("renders title and subtitle content when provided", () => {
    const rendered = CardHeaderView({
      subtitle: "Deployment health",
      title: "Runtime rollout",
    });

    const children = React.Children.toArray(rendered?.props.children);

    expect(children).toHaveLength(2);
    expect(children[0]).toMatchObject({
      props: { children: "Runtime rollout" },
    });
    expect(children[1]).toMatchObject({
      props: { children: "Deployment health" },
    });
  });
});

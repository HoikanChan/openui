import React from "react";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";
import { HLayout } from ".";
import { HLayoutSchema } from "./schema";
import { HLayoutView } from "./view";

describe("react-ui-dsl HLayout schema", () => {
  it("accepts tokenized flex props while rejecting the legacy numeric gap", () => {
    const result = HLayoutSchema.safeParse({
      align: "center",
      children: [],
      gap: "m",
      justify: "between",
      wrap: true,
    });

    expect(result.success).toBe(true);
    expect(
      HLayoutSchema.safeParse({
        children: [],
        gap: 12,
      }).success,
    ).toBe(false);
  });

  it("keeps gap as the second positional arg in the generated signature", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.HLayout.signature).toContain('HLayout(children?: any[], gap?: "none" | "xs"');
    expect(spec.components.HLayout.signature).not.toContain("direction");
  });
});

describe("HLayout renderer", () => {
  it("maps tokenized gap props into the legacy HLayoutView contract", () => {
    const rendered = HLayout.component({
      props: {
        children: ["Body copy"],
        gap: "m",
        wrap: true,
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.type).toBe(HLayoutView);
    expect(rendered.props).toMatchObject({
      children: ["Body copy"],
      gap: 12,
      wrap: true,
    });
  });
});

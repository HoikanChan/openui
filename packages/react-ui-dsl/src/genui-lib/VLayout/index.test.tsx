import React from "react";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";
import { VLayout } from ".";
import { VLayoutSchema } from "./schema";
import { VLayoutView } from "./view";

describe("react-ui-dsl VLayout schema", () => {
  it("accepts tokenized flex props while rejecting the legacy numeric gap", () => {
    const result = VLayoutSchema.safeParse({
      align: "center",
      children: [],
      gap: "l",
      justify: "between",
      wrap: true,
    });

    expect(result.success).toBe(true);
    expect(
      VLayoutSchema.safeParse({
        children: [],
        gap: 16,
      }).success,
    ).toBe(false);
  });

  it("keeps gap as the second positional arg in the generated signature", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.VLayout.signature).toContain('VLayout(children?: any[], gap?: "none" | "xs"');
    expect(spec.components.VLayout.signature).not.toContain("direction");
  });
});

describe("VLayout renderer", () => {
  it("maps tokenized gap props into the legacy VLayoutView contract", () => {
    const rendered = VLayout.component({
      props: {
        children: ["Body copy"],
        gap: "l",
      },
      renderNode: (value) => value as React.ReactNode,
    });

    expect(rendered.type).toBe(VLayoutView);
    expect(rendered.props).toMatchObject({
      children: ["Body copy"],
      gap: 18,
    });
  });
});

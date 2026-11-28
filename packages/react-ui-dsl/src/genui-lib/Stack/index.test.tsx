import React from "react";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";
import { Stack, StackSchema } from "./index";

describe("Stack schema", () => {
  it("accepts valid props", () => {
    const result = StackSchema.safeParse({
      children: [],
      direction: "row",
      align: "center",
      justify: "between",
      wrap: true,
    });
    expect(result.success).toBe(true);
  });

  it("defaults direction to column when omitted", () => {
    const result = StackSchema.safeParse({ children: [] });
    expect(result.success).toBe(true);
  });

  it("rejects invalid direction value", () => {
    expect(StackSchema.safeParse({ direction: "diagonal" }).success).toBe(false);
  });

  it("does not declare a gap prop", () => {
    expect(Object.keys(StackSchema.shape)).not.toContain("gap");
  });

  it("rejects align values outside the contract enum", () => {
    for (const align of ["start", "end", "baseline"]) {
      expect(StackSchema.safeParse({ align }).success).toBe(false);
    }
  });

  it("rejects justify values outside the contract enum", () => {
    for (const justify of ["end", "around", "evenly"]) {
      expect(StackSchema.safeParse({ justify }).success).toBe(false);
    }
  });

  it("keeps direction as second positional prop", () => {
    const spec = dslLibrary.toSpec();
    expect(Object.keys(spec.components.Stack.propsSchema?.properties ?? {})).toEqual([
      "children",
      "direction",
      "align",
      "justify",
      "wrap",
    ]);
    expect(spec.components.Stack.propsSchema?.properties.direction).toEqual({
      type: "string",
      enum: ["row", "column"],
    });
    expect(spec.components.Stack.propsSchema?.properties.align).toEqual({
      type: "string",
      enum: ["center", "stretch"],
    });
    expect(spec.components.Stack.propsSchema?.properties.justify).toEqual({
      type: "string",
      enum: ["start", "center", "between"],
    });
  });
});

describe("Stack renderer", () => {
  it("renders column direction by default", () => {
    const rendered = Stack.component({
      props: { children: ["A"] },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.type).toBe("div");
    expect(rendered.props.style.flexDirection).toBe("column");
    expect(rendered.props.style.gap).toBe("0.75rem");
  });

  it("renders row direction when direction is row", () => {
    const rendered = Stack.component({
      props: { children: ["A"], direction: "row" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.type).toBe("div");
    expect(rendered.props.style.flexDirection).toBe("row");
    expect(rendered.props.style.gap).toBe("0.75rem");
  });

  it("marks row Stacks so nested Stacks can share the row width", () => {
    const row = Stack.component({
      props: { children: ["A"], direction: "row" },
      renderNode: (v) => v as React.ReactNode,
    });
    const column = Stack.component({
      props: { children: ["A"] },
      renderNode: (v) => v as React.ReactNode,
    });

    expect(row.props.className).toBeTruthy();
    expect(column.props.className).toBeTruthy();
    expect(row.props.className).not.toBe(column.props.className);
  });

  it("applies a fixed default gap independent of props", () => {
    const rendered = Stack.component({
      props: {},
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.gap).toBe("0.75rem");
  });

  it("sets flexWrap when wrap is true", () => {
    const rendered = Stack.component({
      props: { wrap: true },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.flexWrap).toBe("wrap");
  });

  it("maps align and justify to flex values", () => {
    const centered = Stack.component({
      props: { align: "center", justify: "center" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(centered.props.style.alignItems).toBe("center");
    expect(centered.props.style.justifyContent).toBe("center");

    const stretched = Stack.component({
      props: { align: "stretch" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(stretched.props.style.alignItems).toBe("stretch");
  });

  it("falls back justifyContent to flex-start when wrap=true and justify=between", () => {
    const rendered = Stack.component({
      props: { wrap: true, justify: "between" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.justifyContent).toBe("flex-start");
  });

  it("keeps justify=between as space-between when wrap is not set", () => {
    const rendered = Stack.component({
      props: { justify: "between" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.justifyContent).toBe("space-between");
  });
});

describe("Stack description", () => {
  it("matches the contract description", () => {
    expect(Stack.description).toBe(
      'Flex container. justify defaults to "start" — omit unless you need "center" or "between" for 3+ items. Params: direction "row"|"column" (default "column"), align "center"|"stretch", justify "start"|"center"|"between", wrap boolean.',
    );
  });

  it("no longer advertises the removed gap prop", () => {
    expect(Stack.description).not.toContain("gap");
  });
});

describe("Stack library registration", () => {
  it("appears in dslLibrary components", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.Stack).toBeDefined();
  });

  it("is the root component", () => {
    const prompt = dslLibrary.prompt({ toolCalls: false, bindings: false });
    expect(prompt).toContain("root = Stack(");
    expect(prompt).not.toContain("root = VLayout(");
  });

  it("VLayout is not registered", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.VLayout).toBeUndefined();
  });

  it("HLayout is not registered", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.HLayout).toBeUndefined();
  });
});

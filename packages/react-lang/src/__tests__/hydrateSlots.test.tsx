import type { ASTNode, ElementNode, EvaluationContext } from "@openuidev/lang-core";
import { describe, expect, it } from "vitest";

import { hydrateSlots } from "../hydrateSlots";

function createLabelBody(text: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Label",
    args: [text],
    mappedProps: {
      text,
    },
  };
}

function createRenderNode(bindings: ASTNode[], body: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Render",
    args: [...bindings, body],
  };
}

const evaluationContext: EvaluationContext = {
  getState: () => null,
  resolveRef: () => null,
};

function renderNode(value: unknown): string {
  if (value == null) return "null";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (typeof value === "object" && value !== null && (value as ElementNode).type === "element") {
    const element = value as ElementNode;
    return `${element.typeName}:${String(element.props.text ?? "")}`;
  }
  return JSON.stringify(value);
}

describe("hydrateSlots", () => {
  it("hydrates the single-variable form nested in plain objects", () => {
    const props = {
      options: {
        cell: createRenderNode([{ k: "Str", v: "v" }], createLabelBody({ k: "Ref", n: "v" })),
      },
    };

    const hydrated = hydrateSlots(props, renderNode, evaluationContext) as typeof props & {
      options: { cell: (value: unknown) => string };
    };

    expect(typeof hydrated.options.cell).toBe("function");
    expect(hydrated.options.cell("Ready")).toBe("Label:Ready");
  });

  it("hydrates the two-variable form with value and row bindings", () => {
    const props = {
      options: {
        cell: createRenderNode(
          [
            { k: "Str", v: "v" },
            { k: "Str", v: "row" },
          ],
          createLabelBody({
            k: "BinOp",
            op: "+",
            left: {
              k: "BinOp",
              op: "+",
              left: { k: "Member", obj: { k: "Ref", n: "row" }, field: "name" },
              right: { k: "Str", v: ":" },
            },
            right: { k: "Ref", n: "v" },
          }),
        ),
      },
    };

    const hydrated = hydrateSlots(props, renderNode, evaluationContext) as typeof props & {
      options: { cell: (value: unknown, row: { name: string }) => string };
    };

    expect(hydrated.options.cell("Open", { name: "Order 1" })).toBe("Label:Order 1:Open");
  });

  it("leaves static ElementNode values unchanged", () => {
    const staticCell: ElementNode = {
      type: "element",
      typeName: "Label",
      props: { text: "Fixed" },
      partial: false,
    };

    const hydrated = hydrateSlots({ options: { cell: staticCell } }, renderNode, evaluationContext) as {
      options: { cell: ElementNode };
    };

    expect(hydrated.options.cell).toBe(staticCell);
  });

  it("recurses into ElementNode props inside arrays", () => {
    const props = {
      columns: [
        {
          type: "element" as const,
          typeName: "Col",
          partial: false,
          props: {
            title: "Status",
            options: {
              cell: createRenderNode([{ k: "Str", v: "v" }], createLabelBody({ k: "Ref", n: "v" })),
            },
          },
        },
      ],
    };

    const hydrated = hydrateSlots(props, renderNode, evaluationContext) as typeof props & {
      columns: Array<{
        props: {
          options: { cell: (value: unknown) => string };
        };
      }>;
    };

    expect(hydrated.columns[0].props.options.cell("Closed")).toBe("Label:Closed");
  });

  it("skips ActionPlan objects", () => {
    const action = {
      steps: [
        {
          type: "set" as const,
          target: "status",
          valueAST: createRenderNode([{ k: "Str", v: "v" }], createLabelBody({ k: "Ref", n: "v" })),
        },
      ],
    };

    const hydrated = hydrateSlots({ action }, renderNode, evaluationContext) as {
      action: typeof action;
    };

    expect(hydrated.action).toBe(action);
    expect(hydrated.action.steps[0].valueAST).toBe(action.steps[0].valueAST);
  });

  it("returns existing functions unchanged", () => {
    const existing = (value: unknown) => `existing:${String(value)}`;

    expect(hydrateSlots(existing, renderNode, evaluationContext)).toBe(existing);
  });
});

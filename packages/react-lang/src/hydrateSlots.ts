import {
  evaluate,
  isASTNode,
  type ASTNode,
  type ElementNode,
  type EvaluationContext,
} from "@openuidev/lang-core";
import type { ReactNode } from "react";

function isActionPlan(value: unknown): value is { steps: unknown[] } {
  if (typeof value !== "object" || value === null || !("steps" in value)) {
    return false;
  }

  const candidate = value as { steps: unknown };
  return Array.isArray(candidate.steps);
}

function isElementNode(value: unknown): value is ElementNode {
  return (
    typeof value === "object" &&
    value !== null &&
    (value as ElementNode).type === "element" &&
    typeof (value as ElementNode).typeName === "string" &&
    typeof (value as ElementNode).props === "object" &&
    (value as ElementNode).props !== null &&
    typeof (value as ElementNode).partial === "boolean"
  );
}

function isRenderSlot(value: unknown): value is ASTNode & { k: "Comp"; name: "Render" } {
  if (!isASTNode(value)) {
    return false;
  }

  const node: ASTNode = value;
  return node.k === "Comp" && node.name === "Render";
}

function getBinderName(arg: ASTNode): string | null {
  if (arg.k === "Str") return arg.v;
  if (arg.k === "Ref") return arg.n;
  return null;
}

function hydrateRenderSlot(
  slot: ASTNode & { k: "Comp"; name: "Render" },
  renderNode: (value: unknown) => ReactNode,
  evaluationContext: EvaluationContext,
): (...args: unknown[]) => ReactNode {
  const binderArgs = slot.args.slice(0, -1);
  const body = slot.args.at(-1);

  return (...args: unknown[]) => {
    if (!body) return null;

    const scopedRefs: Record<string, unknown> = {};
    for (const [index, binderArg] of binderArgs.entries()) {
      const binderName = getBinderName(binderArg);
      if (!binderName) continue;
      scopedRefs[binderName] = args[index];
    }

    const evaluated = evaluate(body, {
      ...evaluationContext,
      resolveRef: (name: string) => {
        if (name in scopedRefs) {
          return scopedRefs[name];
        }
        return evaluationContext.resolveRef(name);
      },
    });

    return renderNode(evaluated);
  };
}

function hydrateElementNode(
  value: ElementNode,
  renderNode: (value: unknown) => ReactNode,
  evaluationContext: EvaluationContext,
): ElementNode {
  const props = hydrateSlots(value.props, renderNode, evaluationContext) as Record<string, unknown>;
  return props === value.props ? value : { ...value, props };
}

export function hydrateSlots(
  value: unknown,
  renderNode: (value: unknown) => ReactNode,
  evaluationContext: EvaluationContext,
): unknown {
  if (typeof value === "function" || value == null) return value;
  if (isRenderSlot(value)) return hydrateRenderSlot(value, renderNode, evaluationContext);
  if (isActionPlan(value)) return value;
  if (Array.isArray(value)) {
    const arrayValue: unknown[] = value;
    let changed = false;
    const next = arrayValue.map((item: unknown) => {
      const hydrated = hydrateSlots(item, renderNode, evaluationContext);
      changed ||= hydrated !== item;
      return hydrated;
    });
    return changed ? next : arrayValue;
  }
  if (isElementNode(value)) {
    return hydrateElementNode(value, renderNode, evaluationContext);
  }
  if (isASTNode(value)) {
    return value;
  }
  if (typeof value === "object") {
    const objectValue = value as Record<string, unknown>;
    let changed = false;
    const next = Object.fromEntries(
      Object.entries(objectValue).map(([key, entryValue]: [string, unknown]) => {
        const hydrated = hydrateSlots(entryValue, renderNode, evaluationContext);
        changed ||= hydrated !== entryValue;
        return [key, hydrated];
      }),
    );
    return changed ? next : objectValue;
  }
  return value;
}

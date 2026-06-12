import type { ElementNode, EvaluationContext, Library } from "@openuidev/lang-core";
import { evaluateElementProps, isElementNode } from "@openuidev/lang-core";
import React from "react";
import type { ComponentRenderProps } from "./library";

export function renderElementNode(
  node: ElementNode | ElementNode[] | unknown,
  library: Library,
  dataModel?: Record<string, unknown>,
): React.ReactNode {
  if (node == null) return null;

  if (Array.isArray(node)) {
    return node.map((n, i) =>
      React.createElement(React.Fragment, { key: i }, renderElementNode(n, library, dataModel))
    );
  }

  if (!isElementNode(node)) {
    if (typeof node === "string") return node;
    if (typeof node === "number") return String(node);
    if (typeof node === "boolean") return String(node);
    return null;
  }

  const Comp = library.components[node.typeName]?.component;
  if (!Comp) return null;

  const evalCtx: EvaluationContext = {
    getState: () => undefined,
    resolveRef: (name: string) => {
      if (name === "data" && dataModel) return dataModel;
      return undefined;
    },
  };

  const evaluatedNode = evaluateElementProps(node, {
    ctx: evalCtx,
    library,
    store: null,
  });

  const renderNode = (child: unknown) => renderElementNode(child, library, dataModel);

  const componentProps: ComponentRenderProps<typeof evaluatedNode.props> = {
    props: evaluatedNode.props,
    renderNode,
  };

  return React.createElement(Comp, componentProps);
}
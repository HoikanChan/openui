import type { ASTNode } from "../parser/ast";

function toLiteralAST(value: unknown): ASTNode {
  if (value === null || value === undefined) return { k: "Null" };
  if (typeof value === "string") return { k: "Str", v: value };
  if (typeof value === "number") return { k: "Num", v: value };
  if (typeof value === "boolean") return { k: "Bool", v: value };
  if (Array.isArray(value)) return { k: "Arr", els: value.map(toLiteralAST) };
  if (typeof value === "object") {
    return {
      k: "Obj",
      entries: Object.entries(value).map(([key, entryValue]) => [key, toLiteralAST(entryValue)]),
    };
  }
  return { k: "Null" };
}

function binderName(node: ASTNode): string | null {
  if (node.k === "Str") return node.v;
  if (node.k === "Ref") return node.n;
  return null;
}

function startsFromCapturedRef(
  node: ASTNode,
  bindings: ReadonlyMap<string, unknown>,
  shadowed: ReadonlySet<string>,
): boolean {
  if (node.k === "Ref") return !shadowed.has(node.n) && bindings.has(node.n);
  if (node.k === "Member") return startsFromCapturedRef(node.obj, bindings, shadowed);
  return false;
}

function withShadowed(
  shadowed: ReadonlySet<string>,
  names: readonly (string | null)[],
): ReadonlySet<string> {
  const next = new Set(shadowed);
  for (const name of names) {
    if (name !== null) next.add(name);
  }
  return next;
}

function instantiateMappedProps(
  mappedProps: Record<string, ASTNode> | undefined,
  bindings: ReadonlyMap<string, unknown>,
  shadowed: ReadonlySet<string>,
): Record<string, ASTNode> | undefined {
  if (mappedProps === undefined) return undefined;
  return Object.fromEntries(
    Object.entries(mappedProps).map(([name, value]) => [
      name,
      instantiateTemplate(value, bindings, shadowed),
    ]),
  );
}

/**
 * Capture the free references in a deferred template without mutating its AST.
 * Nested template binders extend the lexical shadow set for their bodies.
 */
export function instantiateTemplate(
  node: ASTNode,
  bindings: ReadonlyMap<string, unknown>,
  shadowed: ReadonlySet<string> = new Set(),
): ASTNode {
  switch (node.k) {
    case "Ref":
      return !shadowed.has(node.n) && bindings.has(node.n)
        ? toLiteralAST(bindings.get(node.n))
        : node;
    case "Member": {
      const capturedObject = startsFromCapturedRef(node.obj, bindings, shadowed);
      const obj = instantiateTemplate(node.obj, bindings, shadowed);
      if (capturedObject && obj.k === "Obj") {
        const entry = obj.entries.find(([name]) => name === node.field);
        if (entry !== undefined) return entry[1];
      }
      return { ...node, obj };
    }
    case "Index":
      return {
        ...node,
        obj: instantiateTemplate(node.obj, bindings, shadowed),
        index: instantiateTemplate(node.index, bindings, shadowed),
      };
    case "BinOp":
      return {
        ...node,
        left: instantiateTemplate(node.left, bindings, shadowed),
        right: instantiateTemplate(node.right, bindings, shadowed),
      };
    case "UnaryOp":
      return { ...node, operand: instantiateTemplate(node.operand, bindings, shadowed) };
    case "Ternary":
      return {
        ...node,
        cond: instantiateTemplate(node.cond, bindings, shadowed),
        then: instantiateTemplate(node.then, bindings, shadowed),
        else: instantiateTemplate(node.else, bindings, shadowed),
      };
    case "Arr":
      return {
        ...node,
        els: node.els.map((element) => instantiateTemplate(element, bindings, shadowed)),
      };
    case "Obj":
      return {
        ...node,
        entries: node.entries.map(([name, value]) => [
          name,
          instantiateTemplate(value, bindings, shadowed),
        ]),
      };
    case "Comp": {
      if (node.name === "Each" && node.args.length < 3) return node;
      if (node.name === "Render" && node.args.length < 2) return node;

      // Deliberately instantiate both equivalent forms: evaluators read
      // mappedProps while other AST consumers may still inspect positional args.
      const mappedProps = instantiateMappedProps(node.mappedProps, bindings, shadowed);
      if (node.name === "Each") {
        const binder = binderName(node.args[1]);
        const args = [
          instantiateTemplate(node.args[0], bindings, shadowed),
          node.args[1],
          instantiateTemplate(node.args[2], bindings, withShadowed(shadowed, [binder])),
          ...node.args.slice(3).map((arg) => instantiateTemplate(arg, bindings, shadowed)),
        ];
        return mappedProps === undefined ? { ...node, args } : { ...node, args, mappedProps };
      }
      if (node.name === "Render") {
        const binderArgs = node.args.slice(0, -1);
        const body = node.args.at(-1)!;
        const renderShadowed = withShadowed(shadowed, binderArgs.map(binderName));
        const args = [...binderArgs, instantiateTemplate(body, bindings, renderShadowed)];
        return mappedProps === undefined ? { ...node, args } : { ...node, args, mappedProps };
      }
      const args = node.args.map((arg) => instantiateTemplate(arg, bindings, shadowed));
      return mappedProps === undefined ? { ...node, args } : { ...node, args, mappedProps };
    }
    case "Assign":
      return { ...node, value: instantiateTemplate(node.value, bindings, shadowed) };
    case "Str":
    case "Num":
    case "Bool":
    case "Null":
    case "Ph":
    case "StateRef":
    case "RuntimeRef":
      return node;
  }
}

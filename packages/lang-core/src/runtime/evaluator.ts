// ─────────────────────────────────────────────────────────────────────────────
// AST evaluator — resolves AST nodes to runtime values.
// Framework-agnostic. No React imports.
// ─────────────────────────────────────────────────────────────────────────────

import type { ASTNode } from "../parser/ast";
import type { BuiltinRuntimeContext } from "../parser/builtins";
import { ACTION_NAMES, ACTION_STEPS, BUILTINS, LAZY_BUILTINS, toNumber } from "../parser/builtins";
import type { ActionPlan, ActionStep, ElementNode } from "../parser/types";
import { isElementNode } from "../parser/types";
import { isReactiveSchema } from "../reactive";
import { evaluatePropCore } from "./evaluate-prop";
import { instantiateTemplate } from "./template-scope";

/** Optional schema context for reactive-aware evaluation. */
export interface SchemaContext {
  /** Component library — used to look up reactive schemas per prop. */
  library: { components: Record<string, { props: { shape?: Record<string, unknown> } }> };
}

export interface EvaluationContext {
  /** Read $variable from the store */
  getState(name: string): unknown;
  /** Resolve a reference to another declaration's evaluated value */
  resolveRef(name: string): unknown;
  /** Extra scope for $value injection during reactive prop evaluation */
  extraScope?: Record<string, unknown>;
  /** Renderer-supplied runtime defaults for builtin formatting helpers. */
  builtinContext?: BuiltinRuntimeContext;
}

export interface ReactiveAssign {
  __reactive: "assign";
  target: string;
  expr: ASTNode;
}

export function isReactiveAssign(value: unknown): value is ReactiveAssign {
  return typeof value === "object" && value !== null && (value as any).__reactive === "assign";
}

/**
 * Evaluate an AST node to a runtime value.
 */
export function evaluate(
  node: ASTNode,
  context: EvaluationContext,
  schemaCtx?: SchemaContext,
): unknown {
  switch (node.k) {
    // ── Literals ──────────────────────────────────────────────────────────
    case "Str":
      return node.v;
    case "Num":
      return node.v;
    case "Bool":
      return node.v;
    case "Null":
      return null;
    case "Ph":
      return null;

    // ── State references ──────────────────────────────────────────────────
    case "StateRef":
      return context.extraScope?.[node.n] ?? context.getState(node.n);

    // ── References ────────────────────────────────────────────────────────
    case "Ref":
    case "RuntimeRef":
      return context.resolveRef(node.n);

    // ── Collections ───────────────────────────────────────────────────────
    case "Arr":
      return node.els.map((el) => evaluate(el, context));
    case "Obj":
      return Object.fromEntries(node.entries.map(([k, v]) => [k, evaluate(v, context)]));

    // ── Component ─────────────────────────────────────────────────────────
    case "Comp": {
      // Lazy builtins — control their own evaluation
      if (LAZY_BUILTINS.has(node.name)) {
        return evaluateLazyBuiltin(node, context, schemaCtx);
      }
      // Check shared builtin registry first
      const builtin = BUILTINS[node.name];
      if (builtin) {
        const args = node.args.map((a) => evaluate(a, context));
        return builtin.fn(context.builtinContext ?? {}, ...args);
      }
      // Action calls → evaluate to ActionPlan/ActionStep
      if (ACTION_NAMES.has(node.name)) {
        return evaluateActionCall(node.name, node.args, context);
      }
      // If parser already mapped args→props (via materializeExpr), use named props.
      // With schema context: emit ReactiveAssign for StateRef on reactive props.
      // Without schema context: preserve StateRef as AST for evaluate-tree to handle.
      if (node.mappedProps) {
        const def = schemaCtx?.library.components[node.name];
        const props: Record<string, unknown> = {};
        for (const [key, val] of Object.entries(node.mappedProps)) {
          const propSchema = def?.props?.shape?.[key];
          if (val.k === "StateRef" && propSchema && isReactiveSchema(propSchema)) {
            // Reactive schema + StateRef → emit ReactiveAssign marker
            props[key] = {
              __reactive: "assign" as const,
              target: val.n,
              expr: { k: "StateRef" as const, n: "$value" },
            };
          } else if (val.k === "StateRef") {
            // Non-reactive StateRef or no schema context → preserve for evaluate-tree
            props[key] = schemaCtx ? context.getState(val.n) : val;
          } else {
            props[key] = evaluate(val, context, schemaCtx);
          }
        }
        const result: ElementNode = {
          type: "element",
          typeName: node.name,
          props,
          partial: false,
          hasDynamicProps: true,
        };
        // If we have schema context, recursively evaluate nested ElementNodes in props
        if (schemaCtx) {
          for (const [key, val] of Object.entries(props)) {
            if (isElementNode(val)) {
              props[key] = evaluateElementInline(val, context, schemaCtx);
            } else if (Array.isArray(val)) {
              props[key] = val.map((item) =>
                isElementNode(item) ? evaluateElementInline(item, context, schemaCtx) : item,
              );
            }
          }
        }
        return result;
      }
      // After materializeValue, all catalog/unknown components are lowered to
      // ElementNode at parse time. Only builtins and mappedProps Comp nodes
      // reach here. If we somehow get an unmapped Comp, warn and return null.
      console.warn(`[openui] Unexpected unmapped Comp node: ${node.name}`);
      return null;
    }

    // ── Binary operators ──────────────────────────────────────────────────
    case "BinOp": {
      // Short-circuit operators evaluate lazily
      if (node.op === "&&") {
        const left = evaluate(node.left, context);
        return left ? evaluate(node.right, context) : left;
      }
      if (node.op === "||") {
        const left = evaluate(node.left, context);
        return left ? left : evaluate(node.right, context);
      }
      if (node.op === "??") {
        const left = evaluate(node.left, context);
        return left !== null && left !== undefined ? left : evaluate(node.right, context);
      }

      const left = evaluate(node.left, context);
      const right = evaluate(node.right, context);

      switch (node.op) {
        case "+":
          if (typeof left === "string" || typeof right === "string") {
            // Treat null/undefined as "" for string concat to avoid "textnull"
            return String(left ?? "") + String(right ?? "");
          }
          return toNumber(left) + toNumber(right);
        case "-":
          return toNumber(left) - toNumber(right);
        case "*":
          return toNumber(left) * toNumber(right);
        case "/":
          // DSL design choice: division by zero returns 0 instead of JavaScript's Infinity/NaN.
          return toNumber(right) === 0 ? 0 : toNumber(left) / toNumber(right);
        case "%":
          return toNumber(right) === 0 ? 0 : toNumber(left) % toNumber(right);
        case "==":
          // Use loose equality so that e.g. 5 == "5" is true,
          // consistent with the toNumber coercion used by comparison operators.
          return left == right;
        case "!=":
          return left != right;
        case ">":
          return toNumber(left) > toNumber(right);
        case "<":
          return toNumber(left) < toNumber(right);
        case ">=":
          return toNumber(left) >= toNumber(right);
        case "<=":
          return toNumber(left) <= toNumber(right);
        default:
          return null;
      }
    }

    // ── Unary operators ───────────────────────────────────────────────────
    case "UnaryOp":
      if (node.op === "!") {
        return !evaluate(node.operand, context);
      }
      if (node.op === "-") {
        return -toNumber(evaluate(node.operand, context));
      }
      return null;

    // ── Ternary ───────────────────────────────────────────────────────────
    case "Ternary": {
      const cond = evaluate(node.cond, context);
      return cond ? evaluate(node.then, context) : evaluate(node.else, context);
    }

    // ── Member access ─────────────────────────────────────────────────────
    case "Member": {
      const obj = evaluate(node.obj, context) as any;
      if (obj == null) return null;
      // Array pluck: if obj is an array, extract field from every element
      if (Array.isArray(obj)) {
        if (node.field === "length") return obj.length;
        return obj.map((item: any) => item?.[node.field] ?? null);
      }
      return obj[node.field];
    }

    // ── Index access ──────────────────────────────────────────────────────
    case "Index": {
      const obj = evaluate(node.obj, context) as any;
      const idx = evaluate(node.index, context);
      if (obj == null || idx == null) return null;
      if (Array.isArray(obj)) {
        return obj[toNumber(idx)];
      }
      return obj[String(idx)];
    }

    // ── Assignment ────────────────────────────────────────────────────────
    case "Assign":
      return {
        __reactive: "assign" as const,
        target: node.target,
        expr: node.value,
      };
  }
}

/**
 * Strip a ReactiveAssign to its current value in a non-reactive context.
 * When transport args or non-reactive props contain a ReactiveAssign, this
 * resolves it to the current state value (or null if getState is unavailable).
 */
export function stripReactiveAssign(value: unknown, context: EvaluationContext): unknown {
  if (!isReactiveAssign(value)) return value;
  return context.getState(value.target) ?? null;
}

/**
 * Evaluate an ElementNode's props with schema awareness. Used by evaluate()
 * when schema context is available and a Comp produces an ElementNode that
 * needs its own props evaluated with reactive schema detection.
 */
function evaluateElementInline(
  el: ElementNode,
  context: EvaluationContext,
  schemaCtx: SchemaContext,
): ElementNode {
  if (el.hasDynamicProps === false) return el;
  const def = schemaCtx.library.components[el.typeName];
  const evaluated: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(el.props)) {
    const propSchema = def?.props?.shape?.[key];
    evaluated[key] = evaluatePropInline(value, context, schemaCtx, propSchema);
  }
  return { ...el, props: evaluated };
}

/**
 * Evaluate a single prop value with schema awareness.
 * Delegates to shared evaluatePropCore with inline-specific recursion callbacks.
 */
function evaluatePropInline(
  value: unknown,
  context: EvaluationContext,
  schemaCtx: SchemaContext,
  reactiveSchema?: unknown,
): unknown {
  return evaluatePropCore(value, context, schemaCtx, reactiveSchema, {
    recurseElement: (el) => evaluateElementInline(el, context, schemaCtx),
    recurse: (v, rs) => evaluatePropInline(v, context, schemaCtx, rs),
  });
}

/**
 * Evaluate Action/Run/ToAssistant/OpenUrl Comp nodes into ActionPlan/ActionStep values.
 */
function evaluateActionCall(
  name: string,
  args: ASTNode[],
  context: EvaluationContext,
): ActionPlan | ActionStep | null {
  switch (name) {
    case "Action": {
      // Action([step1, step2, ...]) → ActionPlan
      const stepsArg = args.length > 0 ? evaluate(args[0], context) : [];
      const rawSteps = Array.isArray(stepsArg) ? stepsArg : [];
      const steps: ActionStep[] = rawSteps.filter(
        (s): s is ActionStep => s != null && typeof s === "object" && "type" in s,
      );
      return { steps };
    }
    case "Run": {
      // Run(runtimeRef) → ActionStep { type: "run", statementId, refType }
      if (args.length === 0) return null;
      const refNode = args[0];
      if (refNode.k === "RuntimeRef" && refNode.refType !== "data") {
        return { type: ACTION_STEPS.Run, statementId: refNode.n, refType: refNode.refType };
      }
      // Unresolved Ref or external data ref — skip (filtered out by Action's step array)
      return null;
    }
    case "ToAssistant": {
      // ToAssistant("message") or ToAssistant("message", "context")
      const message = args.length > 0 ? String(evaluate(args[0], context) ?? "") : "";
      const ctx = args.length > 1 ? String(evaluate(args[1], context) ?? "") : undefined;
      return { type: ACTION_STEPS.ToAssistant, message, context: ctx };
    }
    case "OpenUrl": {
      // OpenUrl("url")
      const url = args.length > 0 ? String(evaluate(args[0], context) ?? "") : "";
      return { type: ACTION_STEPS.OpenUrl, url };
    }
    case "Set": {
      // Set($varName, value) → ActionStep { type: "set", target, valueAST }
      // First arg must be a StateRef (the $variable), second arg is the value expression.
      // valueAST is preserved as-is and evaluated at click time by triggerAction.
      // Loop variables (e.g. t.id from Each) are captured before deferred evaluation.
      if (args.length < 2) return null;
      const targetNode = args[0];
      if (targetNode.k !== "StateRef") return null;
      return { type: ACTION_STEPS.Set, target: targetNode.n, valueAST: args[1] };
    }
    case "Reset": {
      // Reset($var1, $var2, ...) → ActionStep { type: "reset", targets: [...] }
      // All args must be StateRef nodes. Restores to declared defaults at runtime.
      const targets = args
        .filter((a): a is ASTNode & { k: "StateRef" } => a.k === "StateRef")
        .map((a) => a.n);
      if (targets.length === 0) return null;
      return { type: ACTION_STEPS.Reset, targets };
    }
    default:
      return null;
  }
}

/**
 * Each(array, varName, template) — evaluate template once per array item.
 * varName is user-defined (e.g. `issue`, `ticket`) — no $ prefix collision.
 *
 * Before evaluation, captures free loop references in the template so deferred
 * expressions (like Action/Set steps) keep their lexical scope.
 */
function evaluateLazyBuiltin(
  node: ASTNode & { k: "Comp" },
  context: EvaluationContext,
  schemaCtx?: SchemaContext,
): unknown {
  if (node.name === "Render") {
    return node;
  }

  if (node.name === "Each") {
    if (node.args.length < 3) return [];
    const arr = evaluate(node.args[0], context);
    if (!Array.isArray(arr)) return [];

    const varName =
      node.args[1].k === "Ref" ? node.args[1].n : node.args[1].k === "Str" ? node.args[1].v : null;
    if (!varName) return [];
    const template = node.args[2];

    return arr.map((item, _idx) => {
      const substituted = instantiateTemplate(template, new Map([[varName, item]]));
      const childCtx: EvaluationContext = {
        ...context,
        resolveRef: (refName: string) => {
          if (refName === varName) return item;
          return context.resolveRef(refName);
        },
      };
      const result = evaluate(substituted, childCtx, schemaCtx);
      // If schema context is available and result is an ElementNode, evaluate its props
      if (schemaCtx && isElementNode(result)) {
        return evaluateElementInline(result as ElementNode, childCtx, schemaCtx);
      }
      return result;
    });
  }
  return null;
}

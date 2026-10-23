import { describe, expect, it } from "vitest";
import { parse, type ASTNode } from "../index";
import { evaluate } from "../runtime/evaluator";

const schema = new Map([
  [
    "Table",
    {
      params: [
        { name: "columns", required: true },
        { name: "rows", required: true },
      ],
    },
  ],
  [
    "Col",
    {
      params: [
        { name: "title", required: true },
        { name: "field", required: true },
      ],
    },
  ],
]);

describe("Flatten in upstream Table repairs", () => {
  it("flattens Each-produced row arrays one level without changing render data", () => {
    const data = { groups: [{ rows: [{ name: "Alice" }] }, { rows: [{ name: "Bob" }] }] };
    const result = parse(
      'root = Table([Col("Name", "name")], rows)\nrows = @Flatten(@Each( $groups, group, group.rows))',
      schema,
      "Table",
    );
    expect(result.meta.errors).toEqual([]);
    expect(
      evaluate(result.root?.props.rows as ASTNode, {
        getState: () => data.groups,
        resolveRef: () => null,
      }),
    ).toEqual([{ name: "Alice" }, { name: "Bob" }]);
    expect(data).toEqual({ groups: [{ rows: [{ name: "Alice" }] }, { rows: [{ name: "Bob" }] }] });
  });

  it("flattens exactly one level and uses the contract fallback for nonarrays", () => {
    const result = parse("root = Table([], @Flatten([[[1]], [[2]]]))", schema, "Table");
    expect(
      evaluate(result.root?.props.rows as ASTNode, {
        getState: () => null,
        resolveRef: () => null,
      }),
    ).toEqual([[1], [2]]);
    for (const input of ["null", "42", '"bad"', "{}"]) {
      const node = parse(`root = Table([], @Flatten(${input}))`, schema, "Table").root?.props
        .rows as ASTNode;
      expect(evaluate(node, { getState: () => null, resolveRef: () => null })).toEqual([]);
    }
  });
});

import { describe, expect, it } from "vitest";
import { exportGenerationContract } from "../index";

describe("contract export", () => {
  it("exports the three default capability gaps without losing structured builtin rules", () => {
    const source = {
      contractVersion: "test-v1",
      components: { Table: { description: "Rows" } },
      builtins: [
        {
          name: "Join",
          signature: "Join(array)",
          params: [{ name: "array", type: "string[]" }],
          returnType: "string",
          typeRule: { op: "identity", version: 1 },
        },
      ],
    };
    const result = exportGenerationContract(source);
    expect(result.capabilityGaps.map((gap) => gap.id)).toEqual(["group-by", "top-n", "join"]);
    expect(result.capabilityGaps[0]).toMatchObject({
      kind: "data",
      name: "分组展示",
      phrases: ["按X分组", "每个X下列出", "按X汇总", "去重"],
      fallback: "用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列",
    });
    expect(result.capabilityGaps.flatMap((gap) => gap.aliases)).not.toContain("Join");
    expect(result.builtins).toEqual(source.builtins);
    expect(source).not.toHaveProperty("capabilityGaps");
  });
});

it("carries component gaps through library exports and extension", async () => {
  const { createLibrary, defineComponent } = await import("../index");
  const { z } = await import("zod");
  const gap = {
    id: "map",
    kind: "component" as const,
    name: "地图",
    aliases: ["GeoMap"],
    phrases: ["地图"],
    fallback: "用 Table 展示坐标",
  };
  const library = createLibrary({ components: [], capabilityGaps: [gap] });
  expect(library.toSpec().capabilityGaps).toEqual([gap]);
  const extension = library.extend({
    capabilityGaps: [{ ...gap, id: "tree", aliases: ["TreeGraph"] }],
  });
  expect(extension.toSpec().capabilityGaps?.map((item) => item.id)).toEqual(["map", "tree"]);
  expect(() =>
    library
      .extend({
        components: [
          defineComponent({
            name: "GeoMap",
            props: z.object({}),
            description: "Map",
            component: null,
          }),
        ],
      })
      .toSpec(),
  ).toThrow(/GeoMap/);
  expect(() => library.extend({ capabilityGaps: [gap] }).toSpec()).toThrow(/Duplicate.*map/);
});

const componentGap = {
  id: "map",
  kind: "component",
  name: "Map",
  aliases: ["GeoMap"],
  phrases: ["map"],
  fallback: "Show a Table",
};

it("preserves explicit gaps, including component gaps and an intentionally empty list", () => {
  const source = { contractVersion: "v1", components: {}, capabilityGaps: [componentGap] };
  expect(exportGenerationContract(source).capabilityGaps).toEqual([componentGap]);
  expect(exportGenerationContract({ ...source, capabilityGaps: [] }).capabilityGaps).toEqual([]);
});

it.each([
  [
    "duplicate IDs",
    { capabilityGaps: [componentGap, { ...componentGap, aliases: ["OtherMap"] }] },
    /Duplicate.*map/,
  ],
  [
    "component collision",
    { components: { GeoMap: {} }, capabilityGaps: [componentGap] },
    /alias "GeoMap".*conflicts/,
  ],
  [
    "builtin collision",
    { builtins: [{ name: "Join" }], capabilityGaps: [{ ...componentGap, aliases: ["Join"] }] },
    /alias "Join".*conflicts/,
  ],
  ["invalid kind", { capabilityGaps: [{ ...componentGap, kind: "other" }] }, /kind/],
  ["missing fallback", { capabilityGaps: [{ ...componentGap, fallback: undefined }] }, /fallback/],
  ["invalid aliases", { capabilityGaps: [{ ...componentGap, aliases: [3] }] }, /aliases/],
])("rejects %s before export", (_name, overrides, message) => {
  expect(() =>
    exportGenerationContract({ contractVersion: "v1", components: {}, ...overrides }),
  ).toThrow(message);
});

it("returns independent gap arrays for repeated library and contract exports", async () => {
  const { createLibrary } = await import("../index");
  const library = createLibrary({ components: [] });
  const first = library.toSpec();
  first.capabilityGaps![0].aliases.push("Custom");
  expect(library.toSpec().capabilityGaps![0].aliases).not.toContain("Custom");
  expect(
    exportGenerationContract({ contractVersion: "v1", components: {} }).capabilityGaps[0].aliases,
  ).not.toContain("Custom");
});

it.each([
  [
    "normalized component alias",
    { components: { Table: {} }, capabilityGaps: [{ ...componentGap, aliases: [" @Table "] }] },
    /Table.*conflicts/,
  ],
  [
    "normalized builtin alias",
    { builtins: [{ name: "Join" }], capabilityGaps: [{ ...componentGap, aliases: [" @Join "] }] },
    /Join.*conflicts/,
  ],
  [
    "cross-gap alias",
    { capabilityGaps: [componentGap, { ...componentGap, id: "second", aliases: [" @GeoMap "] }] },
    /GeoMap.*map/,
  ],
  ["blank id", { capabilityGaps: [{ ...componentGap, id: " \t " }] }, /id/],
  ["blank name", { capabilityGaps: [{ ...componentGap, name: " \n " }] }, /name/],
  ["blank fallback", { capabilityGaps: [{ ...componentGap, fallback: " " }] }, /fallback/],
  ["blank alias", { capabilityGaps: [{ ...componentGap, aliases: [" @ "] }] }, /alias/],
  ["blank phrase", { capabilityGaps: [{ ...componentGap, phrases: [" \t "] }] }, /phrase/],
])("rejects SDK-incompatible %s", (_name, overrides, message) => {
  expect(() =>
    exportGenerationContract({ contractVersion: "v1", components: {}, ...overrides }),
  ).toThrow(message);
});

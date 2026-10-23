import { z } from "zod";

/** Known unsupported capabilities, consumed by contract exporters and SDKs. */
export const CapabilityGapSchema = z.object({
  id: z.string().min(1),
  kind: z.enum(["data", "component"]),
  name: z.string().min(1),
  aliases: z.array(z.string().min(1)),
  phrases: z.array(z.string()),
  fallback: z.string().min(1),
});

export type CapabilityGap = z.infer<typeof CapabilityGapSchema>;

// Keep the already shipped ObjectFromEntries alias from ADR-0004.
const DEFAULT_CAPABILITY_GAPS: CapabilityGap[] = [
  {
    id: "group-by",
    kind: "data",
    name: "分组展示",
    aliases: [
      "GroupBy",
      "Distinct",
      "Unique",
      "CountBy",
      "Pivot",
      "Aggregate",
      "Partition",
      "ObjectFromEntries",
    ],
    phrases: ["按X分组", "每个X下列出", "按X汇总", "去重"],
    fallback: "用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列",
  },
  {
    id: "top-n",
    kind: "data",
    name: "截取前 N 条",
    aliases: ["Slice", "Take", "Limit", "TopN"],
    phrases: ["前N", "Top N", "最多的几个"],
    fallback: "用 @Sort 排序后展示全部行，不截取",
  },
  {
    id: "join",
    kind: "data",
    name: "跨数组关联",
    aliases: ["Lookup", "LeftJoin", "InnerJoin", "Zip"],
    phrases: ["关联", "对应到", "合并两张表"],
    fallback: "每个数组分别用一张 Table 展示",
  },
];

/** A fresh copy so callers cannot mutate defaults used by subsequent exports. */
export function defaultCapabilityGaps(): CapabilityGap[] {
  return z.array(CapabilityGapSchema).parse(DEFAULT_CAPABILITY_GAPS);
}

/** Validate gaps against the names actually present in the exported contract. */
export function validateCapabilityGaps(
  gaps: unknown,
  componentNames: Iterable<string>,
  builtinNames: Iterable<string>,
): CapabilityGap[] {
  const parsed = z.array(CapabilityGapSchema).parse(gaps);
  const names = new Set([...componentNames, ...builtinNames]);
  const ids = new Set<string>();
  for (const gap of parsed) {
    if (ids.has(gap.id)) throw new Error(`Duplicate capability gap id: ${gap.id}`);
    ids.add(gap.id);
    for (const alias of gap.aliases) {
      if (names.has(alias)) {
        throw new Error(
          `Capability gap "${gap.id}" alias "${alias}" conflicts with an exported component or builtin`,
        );
      }
    }
  }
  return parsed;
}

// @vitest-environment jsdom
import { createParser } from "@cloudsop/openui-lang-core";
import { Renderer } from "@cloudsop/openui-react-lang";
import { cleanup, render } from "@testing-library/react";
import * as echarts from "echarts";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { dslLibrary } from "../../genui-lib/dslLibrary";

vi.mock("echarts", () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
  registerTheme: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.mocked(echarts.init).mockClear();
});

// ── Contract source of truth ──
//
// The Java SDK owns `base-contract.json`; its `examples` array is the canonical
// acceptance input for the renderer. Read it from disk and validate it at the
// boundary instead of trusting the JSON shape.

const ContractSchema = z.object({
  examples: z.array(z.string()).min(1),
});

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../..");
const contractPath = resolve(
  repoRoot,
  "packages/genui-java-sdk/src/main/resources/openui/base-contract.json",
);

const contract: z.infer<typeof ContractSchema> = ContractSchema.parse(
  JSON.parse(readFileSync(contractPath, "utf8")),
);

const parser = createParser(dslLibrary.toJSONSchema());

// ── Per-example data models ──
//
// Each spec is matched to a contract example by a unique data-path marker. The
// `contains` values are literals the example deterministically renders from the
// data model (or from its own static labels, for chart-only examples).

type ExampleSpec = {
  /** Substring unique to one contract example, used to pair example -> spec. */
  readonly match: string;
  readonly name: string;
  readonly dataModel: Record<string, unknown>;
  readonly contains: readonly string[];
  /** The example mounts at least one echarts instance. */
  readonly expectChart?: boolean;
  /** Today's only reason this example cannot fully render, if any. */
  readonly gated?: string;
};

const EXAMPLE_SPECS: readonly ExampleSpec[] = [
  {
    match: "data.employees",
    name: "employee directory table + KPI row",
    dataModel: {
      employees: [
        {
          id: "e-1",
          name: "Alice Chen",
          salary: 95000,
          joinedAt: "2023-06-15T00:00:00.000Z",
          active: 1,
        },
        {
          id: "e-2",
          name: "Bob Li",
          salary: 82000,
          joinedAt: "2022-01-10T00:00:00.000Z",
          active: 0,
        },
        {
          id: "e-3",
          name: "Cara Wu",
          salary: 76000,
          joinedAt: "2024-03-01T00:00:00.000Z",
          active: 1,
        },
      ],
    },
    contains: ["Employee Directory", "Total Employees", "Alice Chen", "Bob Li", "Inactive"],
  },
  {
    match: "data.samples",
    name: "line chart from [timestamp, value] tuples",
    dataModel: {
      samples: [
        [1717200000000, 12.5],
        [1717203600000, 14.2],
        [1717207200000, 11.8],
        [1717210800000, 15.6],
        [1717214400000, 13.1],
        [1717218000000, 12.0],
      ],
    },
    contains: ["Latency Trend", "Hourly samples, last 6 hours"],
    expectChart: true,
  },
  {
    match: "data.queryDeviceMetricTrend",
    name: "nested sample arrays indexed directly",
    dataModel: {
      queryDeviceMetricTrend: [
        {
          data: [
            {
              data: {
                timestamp: [1717200000000, 1717203600000, 1717207200000],
                value: [35, 42, 38],
              },
            },
            {
              data: {
                timestamp: [1717200000000, 1717203600000, 1717207200000],
                value: [61, 58, 65],
              },
            },
          ],
        },
      ],
    },
    contains: ["AP信道利用率趋势"],
    expectChart: true,
  },
  {
    match: "data.healthScore",
    name: "per-dimension cards with chart and tables",
    dataModel: {
      healthScore: 92,
      data: [
        {
          key: "网络稳定性",
          value: {
            rootValue: 88,
            rank: 2,
            baseline: [
              { timestamp: 1717200000000, value: 80 },
              { timestamp: 1717203600000, value: 84 },
              { timestamp: 1717207200000, value: 86 },
            ],
            essential: { value: [82, 85, 88] },
            rankList: [
              { regionName: "华东", value: 91, level: "A" },
              { regionName: "华南", value: 84, level: "B" },
            ],
            subValues: [
              { key: "丢包率", value: 0.2 },
              { key: "时延", value: 18 },
            ],
          },
        },
        {
          key: "链路质量",
          value: {
            rootValue: 90,
            rank: 1,
            baseline: [
              { timestamp: 1717200000000, value: 88 },
              { timestamp: 1717203600000, value: 90 },
              { timestamp: 1717207200000, value: 91 },
            ],
            essential: { value: [89, 90, 92] },
            rankList: [{ regionName: "华北", value: 93, level: "A" }],
            subValues: [{ key: "误码率", value: 0.01 }],
          },
        },
      ],
    },
    contains: ["网络健康报告", "综合健康分: 92", "网络稳定性", "得分: 88", "华东", "链路质量"],
    expectChart: true,
  },
  {
    match: "data.grouped_by_model",
    name: "nested array rendered as tabs",
    dataModel: {
      grouped_by_model: [
        {
          model: "S5735",
          count: 2,
          devices: [
            { name: "SW-1", ip: "10.0.0.1", version: "V200R019", vendor: "Huawei" },
            { name: "SW-2", ip: "10.0.0.2", version: "V200R019", vendor: "Huawei" },
          ],
        },
        {
          model: "S6730",
          count: 1,
          devices: [{ name: "SW-3", ip: "10.0.1.1", version: "V200R021", vendor: "Huawei" }],
        },
      ],
    },
    contains: ["S5735"],
    // Gated: TabItem is now registered, but Tabs still consumes only its own
    // inline item shape, so a standalone TabItem element assigned then passed to
    // Tabs renders an empty container. Un-gate once Tabs accepts TabItem elements.
    gated: "Tabs does not yet consume standalone TabItem elements",
  },
  {
    match: "data.queryDeviceInfo.deviceInfo",
    name: "nested array flattened into one table",
    dataModel: {
      queryDeviceInfo: {
        deviceInfo: [
          [
            { neName: "Leaf-01", neIp: "10.10.0.1", neRole: "ServerLeaf", neState: "online" },
            { neName: "Leaf-02", neIp: "10.10.0.2", neRole: "BorderLeaf", neState: "offline" },
          ],
        ],
      },
    },
    contains: ["Devices", "Leaf-01", "10.10.0.1", "Server Leaf", "在线"],
    // Gated: lang-core's builtin registry has no Flatten (it was dropped when the
    // branch was scoped to the Java SDK; the contract kept the example). Once
    // Flatten lands, remove it.fails.
    gated: "@Flatten is missing from lang-core builtins",
  },
  {
    match: "data.devicesById",
    name: "object map via ObjectEntries + region pie",
    dataModel: {
      devicesById: {
        "dev-001": { status: "online" },
        "dev-002": { status: "offline" },
      },
      devicesByRegion: { North: 12, South: 8 },
    },
    contains: ["Devices by ID", "dev-001", "online", "Regions"],
    expectChart: true,
  },
  {
    match: "data.mlagGroups",
    name: "grouped members merged and flattened",
    dataModel: {
      totalCount: 2,
      mlagGroups: [
        {
          groupName: "MLAG-A",
          members: [
            { "device-name": "SW-1", "device-ip": "192.168.1.1" },
            { "device-name": "SW-2", "device-ip": "192.168.1.2" },
          ],
        },
        {
          groupName: "MLAG-B",
          members: [{ "device-name": "SW-3", "device-ip": "192.168.2.1" }],
        },
      ],
    },
    contains: ["MLAG 组信息", "共 2 个 MLAG 组", "MLAG-A", "SW-3", "192.168.2.1"],
    // Gated: the same lang-core gap as example 5, plus @MergeObject is absent too.
    // Once both land, remove it.fails.
    gated: "@Flatten and @MergeObject are missing from lang-core builtins",
  },
];

function findSpec(dsl: string): ExampleSpec {
  const spec = EXAMPLE_SPECS.find((candidate) => dsl.includes(candidate.match));
  if (!spec) {
    throw new Error(`No data model spec matches this contract example:\n${dsl}`);
  }
  return spec;
}

function runExample(dsl: string, spec: ExampleSpec): void {
  const parsed = parser.parse(dsl);
  expect(
    parsed.meta.errors,
    `parse errors in "${spec.name}":\n${JSON.stringify(parsed.meta.errors, null, 2)}\n${dsl}`,
  ).toHaveLength(0);
  expect(parsed.root, `"${spec.name}" parsed to no root element:\n${dsl}`).not.toBeNull();

  const { container } = render(
    <Renderer library={dslLibrary} response={dsl} dataModel={spec.dataModel} />,
  );

  const html = container.innerHTML;
  expect(html.trim().length, `"${spec.name}" rendered empty output`).toBeGreaterThan(0);
  expect(html, `"${spec.name}" rendered an unknown-component marker`).not.toContain(
    "Unknown component",
  );

  for (const text of spec.contains) {
    expect(html, `"${spec.name}" should render ${JSON.stringify(text)}:\n${dsl}`).toContain(text);
  }

  if (spec.expectChart) {
    expect(vi.mocked(echarts.init), `"${spec.name}" should mount a chart`).toHaveBeenCalled();
  }
}

describe("base contract examples", () => {
  it("has a data model spec for every contract example", () => {
    const matched = contract.examples.map((dsl) => findSpec(dsl).name);
    expect(matched).toHaveLength(contract.examples.length);
    expect(new Set(matched).size, "two examples may have matched the same spec").toBe(
      contract.examples.length,
    );
  });

  for (const [index, dsl] of contract.examples.entries()) {
    const spec = findSpec(dsl);
    const title = `example ${index}: ${spec.name}`;

    if (spec.gated) {
      it.fails(`${title} (gated: ${spec.gated})`, () => runExample(dsl, spec));
    } else {
      it(title, () => runExample(dsl, spec));
    }
  }
});

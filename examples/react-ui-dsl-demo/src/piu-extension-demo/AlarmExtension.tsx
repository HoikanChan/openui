import { createLibrary, defineComponent } from "@openuidev/react-ui-dsl";
import { z } from "zod";

export const ALARM_EXTENSION_ID = "piu-alarm-runtime-demo";

export const DEFAULT_ALARM_PROMPT =
  "生成一个区域告警概览 UI。先调用 queryAlarmSummary 查询华东一区最近 1 小时的告警汇总，然后用 AlarmSummaryCard 展示总数、Critical、Major、Minor 数量。只输出 openui-lang。";

export const DEFAULT_ALARM_DATA_MODEL = {
  region: "华东一区",
  window: "1h",
};

const AlarmSummaryCardSchema = z.object({
  title: z.string(),
  total: z.number(),
  critical: z.number(),
  major: z.number(),
  minor: z.number(),
});

export const AlarmSummaryCard = defineComponent({
  name: "AlarmSummaryCard",
  description:
    "告警汇总卡片，用于展示一个区域内的总告警数和 critical/major/minor 三类告警数量。",
  props: AlarmSummaryCardSchema,
  component: ({ props }) => {
    const items = [
      { label: "Critical", value: props.critical, color: "#cf1322", bg: "#fff1f0" },
      { label: "Major", value: props.major, color: "#d46b08", bg: "#fff7e6" },
      { label: "Minor", value: props.minor, color: "#ad8b00", bg: "#feffe6" },
    ];

    return (
      <section
        data-testid="alarm-summary-card"
        style={{
          width: "min(560px, 100%)",
          border: "1px solid #d9e2ec",
          borderRadius: 8,
          background: "#ffffff",
          boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",
          padding: 18,
          fontFamily: "Inter, system-ui, sans-serif",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
          <div>
            <div style={{ fontSize: 13, color: "#5b677a", marginBottom: 4 }}>
              Alarm Overview
            </div>
            <h2 style={{ margin: 0, fontSize: 20, color: "#1f2937" }}>{props.title}</h2>
          </div>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: 12, color: "#6b7280" }}>Total</div>
            <strong style={{ fontSize: 34, color: "#111827", lineHeight: 1 }}>
              {props.total}
            </strong>
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, marginTop: 18 }}>
          {items.map((item) => (
            <div
              key={item.label}
              style={{
                borderRadius: 6,
                background: item.bg,
                padding: "10px 12px",
                border: "1px solid rgba(15, 23, 42, 0.08)",
              }}
            >
              <div style={{ color: item.color, fontSize: 12, fontWeight: 700 }}>
                {item.label}
              </div>
              <div style={{ color: item.color, fontSize: 24, fontWeight: 800 }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  },
});

export async function queryAlarmSummary(args: Record<string, unknown>) {
  const region = typeof args.region === "string" ? args.region : DEFAULT_ALARM_DATA_MODEL.region;
  const window = typeof args.window === "string" ? args.window : DEFAULT_ALARM_DATA_MODEL.window;

  return {
    region,
    window,
    total: 42,
    critical: 7,
    major: 15,
    minor: 20,
  };
}

export const runtimeExtension = {
  extensionId: ALARM_EXTENSION_ID,
  components: [AlarmSummaryCard],
  tools: [
    {
      name: "queryAlarmSummary",
      description: "查询指定区域和时间窗口内的告警汇总数量。",
      inputSchema: {
        type: "object",
        properties: {
          region: { type: "string" },
          window: { type: "string" },
        },
        required: ["region", "window"],
      },
      annotations: { readOnlyHint: true },
    },
  ],
  toolProvider: {
    queryAlarmSummary,
  },
};

export const generationExtension = {
  extensionId: ALARM_EXTENSION_ID,
  version: "1.0.0",
  components: createLibrary({ components: [AlarmSummaryCard] }).toSpec().components,
  componentGroups: [
    {
      name: "Alarm Operations",
      components: ["AlarmSummaryCard"],
      notes: [
        "告警概览场景优先使用 AlarmSummaryCard。",
        "需要实时告警汇总时先用 Query(\"queryAlarmSummary\", ...) 获取数据。",
      ],
    },
  ],
  tools: runtimeExtension.tools,
  examples: [
    'alarmData = Query("queryAlarmSummary", {region: "华东一区", window: "1h"}, {total: 0, critical: 0, major: 0, minor: 0})\nroot = AlarmSummaryCard("华东一区告警概览", alarmData.total, alarmData.critical, alarmData.major, alarmData.minor)',
  ],
  additionalRules: [
    "如果用户要求告警概览，必须先调用 queryAlarmSummary，再使用 AlarmSummaryCard 渲染结果。",
    "AlarmSummaryCard 的参数顺序是 title, total, critical, major, minor。",
    "每个变量名只能定义一次：Query 的结果变量（如 alarmData）必须与 AlarmSummaryCard 的变量名不同，严禁用同一个标识符既接收 Query 结果又赋值组件。",
    "只有一个 AlarmSummaryCard 时，直接把它赋给 root（root = AlarmSummaryCard(...)），不要用 Stack 等容器包裹。",
  ],
};

declare global {
  interface Window {
    __OPENUI_ALARM_RUNTIME_EXTENSION__?: typeof runtimeExtension;
  }
}

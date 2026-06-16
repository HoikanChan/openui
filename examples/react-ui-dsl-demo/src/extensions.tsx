// 组件型扩展的前端运行时配套:与 GenUI Service 的 seed(seed/*.json)成对出现。
// 后端 seed 提供模型可见的 Component Contract(进 prompt),这里用
// dslLibrary.extend() 注册对应的 React 渲染实现(进 Renderer/parser)。
// 两侧的组件名与 props 必须一致,否则模型生成的组件前端渲染不出来。
import { defineComponent, type Library } from "@openuidev/react-lang";
import { dslLibrary } from "@openuidev/react-ui-dsl";
import { z } from "zod";

const AlarmBadgeSchema = z.object({
  severity: z.enum(["critical", "major", "minor"]),
  count: z.number(),
  label: z.string().optional(),
});

const SEVERITY_STYLES: Record<string, { bg: string; border: string; text: string; name: string }> = {
  critical: { bg: "#fff1f0", border: "#ffa39e", text: "#cf1322", name: "Critical" },
  major: { bg: "#fff7e6", border: "#ffd591", text: "#d46b08", name: "Major" },
  minor: { bg: "#feffe6", border: "#fffb8f", text: "#ad8b00", name: "Minor" },
};

export const AlarmBadge = defineComponent({
  name: "AlarmBadge",
  // 运行时描述;模型可见的契约由 GenUI Service 的 seed/noe-biz-components.json 提供
  description: "告警徽章:按严重级别着色展示告警数量",
  props: AlarmBadgeSchema,
  component: ({ props }) => {
    const style = SEVERITY_STYLES[props.severity] ?? SEVERITY_STYLES["minor"]!;
    return (
      <span
        data-testid="alarm-badge"
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: 6,
          padding: "2px 10px",
          borderRadius: 999,
          background: style.bg,
          border: `1px solid ${style.border}`,
          color: style.text,
          fontSize: 13,
          fontWeight: 500,
          lineHeight: "20px",
        }}
      >
        {props.label ?? style.name}
        <strong style={{ fontSize: 15 }}>{props.count}</strong>
      </span>
    );
  },
});

/** contextId → 前端运行时扩展库;extend 不可变,base dslLibrary 不受影响。 */
const extensionsByContext: Record<string, Library> = {
  "noe-biz-components": dslLibrary.extend({ components: [AlarmBadge] }),
};

export function libraryForContext(contextId?: string): Library {
  return (contextId && extensionsByContext[contextId]) || dslLibrary;
}

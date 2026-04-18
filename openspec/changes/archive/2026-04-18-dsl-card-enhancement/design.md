## Context

当前 `packages/react-ui-dsl/src/genui-lib/Card` 的结构：

- 底层依赖 `antd/Card`
- Schema 字段：`properties.tag`（标题前缀）、`properties.header`（string）、`properties.headerAlign`（left/center/right）、`children`、`style`
- `tag` 和 `headerAlign` 是 Ant Design 渲染逻辑的产物，对 LLM 来说语义模糊
- 无法表达副标题或右上角操作区

react-ui 的 `Card` + `CardHeader` 是这次设计的参考基准：`Card` 是简单容器负责视觉变体，`CardHeader` 负责标题区（title、subtitle、右侧 actions）。DSL Card 参考这一结构，但自行用 `div` 实现，不引入 react-ui 包依赖。

## Goals / Non-Goals

**Goals:**

- 用自实现 `div` 结构完全替换 `antd/Card` 依赖。
- 引入 `variant`（card / clear / sunk）和 `width`（standard / full），与 react-ui Card 的外观控制对齐。
- 将 `header` 从字符串升级为对象，支持 `title`、`subtitle`、`actions`（DSL 节点数组，用于渲染 Button/Link 等操作）。
- 更新 dslLibrary 描述和示例，让 LLM 默认产出新 schema。

**Non-Goals:**

- 引入 react-ui 包作为 DSL 的运行时依赖。
- 为 Card 加动画、折叠、loading 骨架等复杂交互状态。
- 对其他 DSL 组件做同步重构。

## Decisions

### 自实现 div 而非引入 react-ui

DSL 包需要保持轻量和独立，不应将 react-ui 作为运行时依赖引入。参考 react-ui 的 API 设计（variant / width / header 结构）即可，样式通过 inline style 或 CSS 变量实现。

Alternative considered: 直接 import react-ui Card 组件。
Rejected because 会引入不必要的样式依赖，也让 DSL 包与 react-ui 版本耦合。

### header 升级为对象而非扁平化字段

参考 react-ui CardHeader 的设计，将 title / subtitle / actions 封装进 `header` 对象，比在顶层展开三个字段（`headerTitle`、`headerSubtitle`、`headerActions`）更整洁，也更符合 openui lang 的嵌套 schema 习惯。

Alternative considered: 在 `properties` 顶层分别暴露 `title`、`subtitle`、`actions`。
Rejected because 字段数量多，顶层会显得臃肿，且无法清晰表达"这些是 header 的属性"。

### 删除 tag 和 headerAlign

`tag` 是对 Ant Design 标题前缀的 hack，`headerAlign` 是对 Ant Design `title` 的文本对齐控制。两者都不属于语义化的组件 API。新 schema 用 `header.title` 取代，对齐需求可通过 `style` 传递。

Alternative considered: 保留 `tag` / `headerAlign` 做兼容。
Rejected because 保留旧字段会让 LLM 产出混合 schema，增加 prompt 歧义。

### actions 为 DSL Node 数组

`header.actions` 接受 DSL 节点数组（`z.array(z.any())`），由 `renderNode` 渲染，允许放 Button、Link 等任意 DSL 组件。这与 openui lang 的组合式组件模式一致。

Alternative considered: actions 为字符串数组（只支持文字按钮）。
Rejected because 限制了表达能力，LLM 常常需要生成带图标或带链接的操作区。

## New Schema

字段直接铺平，不套 `properties` 对象，与 Table 新 schema 风格一致。

```ts
// schema.ts
export const CardHeaderSchema = z.object({
  title: z.string().optional(),
  subtitle: z.string().optional(),
  actions: z.array(z.any()).optional(),
}).optional();

export const CardSchema = z.object({
  variant: z.enum(["card", "clear", "sunk"]).optional(),
  width: z.enum(["standard", "full"]).optional(),
  header: CardHeaderSchema,
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
```

## Component Implementation Sketch

样式通过 **CSS Modules**（`card.module.css`）实现，不使用 inline style 拼接 variant。`props.style` 仍然透传给根元素，允许消费方做尺寸/位置微调。

```
Card/
  index.tsx
  card.module.css
  schema.ts
```

```css
/* card.module.css */
.root {
  border-radius: 8px;
  overflow: hidden;
}
.widthStandard { /* no forced width, let parent control */ }
.widthFull { width: 100%; }

.variantCard {
  border: 1px solid var(--card-border, #333);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  background: var(--card-bg, #1a1f2e);
}
.variantClear { /* intentionally empty — no border/bg */ }
.variantSunk {
  background: var(--card-sunk-bg, #0d1117);
  border: 1px solid var(--card-sunk-border, #222);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid var(--card-border, #333);
}
.headerLeft { display: flex; flex-direction: column; gap: 2px; }
.headerTitle { font-weight: 600; }
.headerSubtitle { font-size: 12px; color: var(--text-secondary, #888); }
.headerActions { display: flex; gap: 8px; }
.body { padding: 12px 16px; }
```

```tsx
// index.tsx
import clsx from "clsx";
import styles from "./card.module.css";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description: "Card container with optional header (title, subtitle, actions) and visual variant",
  component: ({ props, renderNode }) => {
    const { variant = "card", width = "standard", header } = props;
    const hasHeader = header && (header.title || header.subtitle || header.actions?.length);

    return (
      <div
        className={clsx(
          styles.root,
          styles[`variant${variant.charAt(0).toUpperCase() + variant.slice(1)}`],
          width === "full" ? styles.widthFull : styles.widthStandard,
        )}
        style={props.style as React.CSSProperties | undefined}
      >
        {hasHeader && (
          <div className={styles.header}>
            <div className={styles.headerLeft}>
              {header.title && <div className={styles.headerTitle}>{header.title}</div>}
              {header.subtitle && <div className={styles.headerSubtitle}>{header.subtitle}</div>}
            </div>
            {header.actions?.length && (
              <div className={styles.headerActions}>{renderNode(header.actions)}</div>
            )}
          </div>
        )}
        <div className={styles.body}>{renderNode(props.children)}</div>
      </div>
    );
  },
});
```

## Risks / Trade-offs

- [CSS Modules 的类名拼接（variantCard 等）若 variant 值不规范会静默失效] → 在 schema 层用 `z.enum` 约束合法值，渲染时做首字母大写映射，无需运行时判断。
- [CSS 变量的颜色值在浅色主题下可能失效] → 使用 CSS 变量并提供合理 fallback，消费方可通过覆盖变量适配主题。
- [旧 prompt 仍会产出 tag / headerAlign] → 同步更新 dslLibrary 的描述文本和示例，让旧字段不再出现在 LLM context。
- [actions 渲染未知节点类型会静默失败] → renderNode 已有空值保护，DSL 运行时负责错误边界，Card 不额外处理。

## Open Questions

- CSS 变量的具体命名（`--card-bg`、`--card-border` 等）是否需要对齐已有的 react-ui token 体系？
- `padding: "12px 16px"` 是否应该也通过 schema 里的 `style` 覆盖，还是硬编码即可？

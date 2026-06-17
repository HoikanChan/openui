# ADR-0005: 画布单视图/多视图模式设计

## 状态

已采纳

## 背景

当前画布（Canvas）始终以多Tab模式运行：DashboardTab 与多个 PreviewTab 共存，通过 Antd `<Tabs type="editable-card">` 页签栏切换。在部分业务场景中（如嵌入式单功能面板、对话式逐步展示），用户不需要多Tab切换能力，期望画布以单一视图方式呈现，同一时间只展示一种内容类型（CanvasCard 网格或 PreviewCard 全页内容），且不显示 Tab 页签栏。

核心矛盾：CanvasCard 与 PreviewCard 在多Tab模式下可共存，但在单视图模式下必须互斥——画布同一时间只能处于"Dashboard网格"或"单Preview全页"之一。

## 决策

在 canvasStore 中新增 `enableMultiTab` 属性（默认 `false`），通过 `setEnableMultiTab()` 方法动态切换。单视图模式下：
- 隐藏 Tab 页签栏，直接渲染当前 activeKey 对应的内容
- CanvasCard 与 PreviewCard 互斥，add 操作时自动清除对方类型的内容

---

## 一、设计思路

### 1.1 核心原则

| 原则 | 说明 |
|------|------|
| **配置优于内容** | `enableMultiTab` 属于画布配置，不是内容数据。`clear()` 重置内容但不重置此配置 |
| **互斥优先于共存** | 单视图模式下，addCanvasCard 清 previewTabs，addPreviewCard 清 canvasCards——不存在混合态 |
| **最小侵入** | DSL 组件（CanvasCard/PreviewCard/HTMLLoader）调用接口不变，行为差异由 store 内部根据 enableMultiTab 自动处理 |
| **视图驱动** | CanvasTabs 组件根据 `state.enableMultiTab` 决定渲染策略，不引入新的 UI 组件 |

### 1.2 模式对比

| 维度 | 多视图模式 (`enableMultiTab=true`) | 单视图模式 (`enableMultiTab=false`) |
|------|-------------------------------------|-------------------------------------|
| Tab 页签栏 | Antd `<Tabs>` 可见 | 不渲染 `<Tabs>`，直接输出内容 |
| CanvasCard 与 PreviewCard | 可共存（不同 Tab） | 互斥，同一时间只有一种 |
| PreviewCard add 行为 | 支持 tabId 定位、replace/append | 固定 tabId=`"preview-single"`，每次 replace |
| CanvasCard add 行为 | 去重追加/替换 | 存量是 CanvasCard → 追加；存量是 PreviewCard → 清除后追加 |
| Tab 关闭交互 | 用户可点击 × 关闭 PreviewTab | 无 Tab 页签，仅"清空画布"按钮 |

---

## 二、规格

### 2.1 State 扩展

```ts
interface CanvasStoreState {
  canvasCards: CanvasCardData[];
  previewTabs: PreviewTabData[];
  activeKey: string;
  enableMultiTab: boolean;  // 新增，默认 false
}
```

### 2.2 Store 公共接口扩展

```ts
interface CanvasStore {
  // ... 原有属性
  enableMultiTab: boolean;                              // getter

  // ... 原有方法
  setEnableMultiTab(enabled: boolean): void;            // 新增
}
```

### 2.3 getSnapshot 扩展

```ts
getSnapshot(): CanvasStoreState {
  return {
    canvasCards: [...state.canvasCards],
    previewTabs: state.previewTabs.map(t => ({ ...t, cards: [...t.cards] })),
    activeKey: state.activeKey,
    enableMultiTab: state.enableMultiTab,               // 新增
  };
}
```

---

## 三、行为规格

### 3.1 addCanvasCard 行为

```
addCanvasCard(card, cardId?)

  if enableMultiTab === false:
    if previewTabs.length > 0:
      previewTabs = []           ← 清除所有 PreviewCard

  // 以下逻辑与多视图模式相同
  resolvedCardId = cardId ?? generateCardId()
  if cardId 且存在同 cardId 的现有卡片:
    替换该卡片
  else:
    追加新卡片
  activeKey = "canvas"
  notify()
  return resolvedCardId
```

**互斥矩阵：**

| 存量类型 | 新增 CanvasCard | 结果 |
|----------|----------------|------|
| CanvasCard(s) | Card B | 追加，canvasCards.length += 1 |
| PreviewCard(s) | Card A | 清 previewTabs，追加 Card A，activeKey = "canvas" |
| 空 | Card A | 追加 Card A，activeKey = "canvas" |

### 3.2 addPreviewCard 行为

```
addPreviewCard(card, tabId?, type?)

  if enableMultiTab === false:
    canvasCards = []              ← 清除所有 CanvasCard
    previewTabs = []              ← 清除所有存量 PreviewTab
    previewTabs.push({
      tabId: "preview-single",    ← 固定 tabId
      title: card.title ?? "Preview",
      cards: [newCard],
    })
    activeKey = "preview-single"
    notify()
    return resolvedCardId

  // 多视图模式逻辑不变（tabId 定位、replace/append）
```

**互斥矩阵：**

| 存量类型 | 新增 PreviewCard | 结果 |
|----------|-----------------|------|
| CanvasCard(s) | Preview | 清 canvasCards，创建 preview-single Tab |
| PreviewCard(s) | Preview B | 清存量 previewTabs，创建 preview-single（替换） |
| 空 | Preview | 创建 preview-single Tab |

**关键约束：**
- 单视图模式下 `tabId` 和 `type` 参数被忽略，固定使用 `"preview-single"`
- 每次调用 addPreviewCard 均为完全替换，不存在 append

### 3.3 setEnableMultiTab 行为

```
setEnableMultiTab(enabled)

  state.enableMultiTab = enabled

  if enabled === false:
    // 切换到单视图时，清除与当前 activeKey 不匹配的互斥内容
    if activeKey === "canvas" && previewTabs.length > 0:
      previewTabs = []
    else if activeKey !== "canvas" && canvasCards.length > 0:
      canvasCards = []

  notify()
```

**切换矩阵：**

| 当前 activeKey | 切换到单视图后 | 清除内容 |
|---------------|--------------|---------|
| "canvas" | 保留 canvasCards | 清 previewTabs |
| preview-tab tabId | 保留 previewTabs | 清 canvasCards |

### 3.4 clear 行为

```
clear()

  canvasCards = []
  previewTabs = []
  activeKey = "canvas"
  // enableMultiTab 不重置——属于配置，不是内容
  notify()
```

### 3.5 removeCanvasCard / removePreviewCard / removePreviewTab

这些方法的行为在两种模式下相同，不受 `enableMultiTab` 影响。单视图模式下的删除后 fallback 行为：

- removeCanvasCard 导致 canvasCards 为空 → `fallbackActiveKey` 检查 previewTabs 是否有数据
- removePreviewTab 导致 previewTabs 为空 → `fallbackActiveKey` 检查 canvasCards 是否有数据

---

## 四、UI 渲染规格

### 4.1 CanvasTabs 条件渲染

```
CanvasTabs 组件:

  if !hasData:
    → 渲染空状态提示（与原行为相同）

  if enableMultiTab === false:
    → 单视图模式渲染：
      if activeKey === "canvas":
        → <DashboardGrid>
      else:
        → 取 previewTabs[0] 渲染内容：
          - iframe 类型 → <HTMLLoaderEmbed>
          - DSL 类型   → <div> + renderElementNode(children)
      外层包装：div + 清空画布按钮（不使用 Antd <Tabs>）

  if enableMultiTab === true:
    → 多视图模式渲染（原行为不变）：
      <Tabs type="editable-card">
        Dashboard tab + Preview tabs
```

**关键区别：** 单视图模式不使用 Antd `<Tabs>` 组件，避免出现页签栏，只渲染内容区域。

### 4.2 DSL 组件影响

| 组件 | 影响 |
|------|------|
| CanvasCard | 无变更。仍调用 `canvasStore.addCanvasCard()`，store 内部根据模式处理互斥 |
| PreviewCard | 无变更。仍调用 `canvasStore.addPreviewCard()`，store 内部根据模式处理互斥 |
| HTMLLoader | 无变更。仍作为 PreviewCard 子组件被提取 |

---

## 五、DFX

### 5.1 可靠性

| 场景 | 处理 |
|------|------|
| enableMultiTab 动态切换 | 切换时根据 activeKey 清除互斥内容，确保不存在混合态 |
| 单视图下 addPreviewCard 的 tabId 参数 | 被忽略，固定 `"preview-single"`，避免参数语义歧义 |
| 单视图下连续 addCanvasCard | 正常追加，与多视图行为一致 |
| 单视图下 addPreviewCard 后再 addCanvasCard | 清 previewTabs → 追加 canvasCard → activeKey = "canvas" |

### 5.2 性能

| 关注点 | 方案 |
|--------|------|
| 单视图模式渲染开销 | 不构建 tabItems 数组，不调用 `buildTabsFromState`，直接根据 activeKey 渲染单一内容 |
| 状态通知频率 | 与多视图模式相同，每次 add/remove/set 操作触发一次 notify |
| getSnapshot 克隆开销 | 不变——仍为浅拷贝（数组 + tabs.map），enableMultiTab 为原始值无需克隆 |

### 5.3 可维护性

| 关注点 | 方案 |
|--------|------|
| DSL 组件零改动 | CanvasCard/PreviewCard/HTMLLoader 调用接口完全不变，所有行为差异内聚在 canvasStore |
| 单/双模式逻辑分离 | store 方法以 `if (!state.enableMultiTab)` 前置拦截，后续逻辑与多视图模式相同，无代码耦合 |
| UI 分支清晰 | CanvasTabs 以 `if (!state.enableMultiTab)` 分流，单视图分支不引入新子组件 |

### 5.4 可测试性

| 关注点 | 方案 |
|--------|------|
| 模式切换测试 | `setEnableMultiTab` 可动态切换，测试用例可在 beforeEach 中设定模式 |
| 互斥规则覆盖 | 单视图 addCanvasCard 和 addPreviewCard 各 3 条互斥矩阵均覆盖 |
| 配置持久性 | `clear()` 不重置 enableMultiTab，有专门测试验证 |

### 5.5 兼容性

| 关注点 | 方案 |
|--------|------|
| 默认值 false | 不影响已开启多视图的存量场景（显式调用 `setEnableMultiTab(true)`） |
| API 向后兼容 | 新增属性和方法，不修改原有方法签名 |
| 导出完整 | `CanvasStore` 类型接口已包含 `enableMultiTab` 和 `setEnableMultiTab`，通过 index.ts 导出 |

---

## 六、测试用例

### 6.1 canvasStore 单元测试 (`canvasStore.test.ts`)

#### 6.1.1 多视图模式测试（原有，beforeEach 设置 `enableMultiTab=true`）

| 测试 | 验证点 |
|------|--------|
| adds a card and returns cardId | CanvasCard 基础追加 |
| adds a card with custom cardId | 自定义 cardId |
| replaces card with same cardId | 去重替换 |
| adds multiple cards with different cardIds | 多卡片追加 |
| switches activeKey to canvas | activeKey 切换 |
| adds a preview tab and switches activeKey | PreviewCard 基础创建 |
| always creates new tab when no tabId provided | 无 tabId → 自动生成 |
| replaces card in existing tab (type=replace) | tabId 定位 + replace |
| appends card to existing tab (type=append) | tabId 定位 + append |
| creates new tab when tabId not found | tabId 不存在 → 新 Tab |
| uses provided tabId when creating new tab | 自定义 tabId |
| adds multiple preview tabs with different titles | 多 Tab 创建 |
| removes a specific card | removeCanvasCard |
| removes the tab when last card is removed | removePreviewCard → Tab 自动删除 |
| switches activeKey away from removed tab | removePreviewTab → fallback |
| removes all cards, tabs, resets activeKey | clear() |
| hasData 各场景 | hasData getter |
| subscribe / unsubscribe | 通知机制 |

#### 6.1.2 单视图模式测试（新增，beforeEach 设置 `enableMultiTab=false`）

| 测试 | 验证点 | 互斥矩阵行 |
|------|--------|-----------|
| appends canvasCard when existing is canvasCards | CanvasCard + CanvasCard → 追加 | 存量Canvas + 新增Canvas |
| clears previewTabs and shows canvasCard when existing is previewCard | PreviewCard + CanvasCard → 清 Preview | 存量Preview + 新增Canvas |
| replaces existing previewCard with canvasCard | 同上，额外验证 previewTabs 被清空 | 存量Preview + 新增Canvas |
| creates single preview tab and clears canvasCards | CanvasCard + PreviewCard → 清 Canvas | 存量Canvas + 新增Preview |
| replaces previous previewCard with new one | PreviewCard + PreviewCard → 替换 | 存量Preview + 新增Preview |
| ignores tabId and type parameters | tabId/type 参数被忽略 | 参数覆盖 |
| defaults to false | enableMultiTab 默认值 | 初始状态 |
| can be toggled to true | setEnableMultiTab(true) | 配置切换 |
| preserves enableMultiTab through clear() | clear() 不重置配置 | 配置持久性 |
| clears conflicting content when switching to single-view | 切换到单视图时清除互斥内容 | 模式切换 |

### 6.2 集成测试 (`canvas-integration.test.ts`)

#### 6.2.1 多视图模式测试（原有，beforeEach 设置 `enableMultiTab=true`）

| 测试 | 验证点 |
|------|--------|
| CanvasCard pushes children to canvasStore | DSL → Store 数据传递 |
| CanvasCard with same cardId replaces existing | 去重 |
| CanvasCard with different cardId adds as new | 多卡片 |
| PreviewCard pushes children as PreviewTab | DSL → Store 数据传递 |
| PreviewCard with HTMLLoader children | url/iframeId/data 提取 |
| PreviewCard with tabId replaces (type=replace) | tabId 定位 + replace |
| PreviewCard with tabId appends (type=append) | tabId 定位 + append |
| PreviewCard with unknown tabId creates new tab | tabId 不存在 → 新 Tab |
| PreviewCard without tabId always creates new tab | 无 tabId → 自动生成 |

#### 6.2.2 单视图模式测试（新增，beforeEach 设置 `enableMultiTab=false`）

| 测试 | 验证点 |
|------|--------|
| PreviewCard clears canvasCards and creates single preview tab | 存量Canvas + 新增Preview → 清Canvas |
| PreviewCard replaces previous preview in single-view mode | 存量Preview + 新增Preview → 替换 |
| CanvasCard clears previewTabs in single-view mode | 存量Preview + 新增Canvas → 清Preview |

---

## 七、文件改动清单

| 文件 | 改动 |
|------|------|
| `packages/react-ui-dsl/src/canvas/canvasStore.ts` | State 加 `enableMultiTab`，新增 `setEnableMultiTab`，`addCanvasCard` 前置单视图拦截，`addPreviewCard` 前置单视图拦截（固定 `"preview-single"`），`getSnapshot` 包含 enableMultiTab，getter 加 enableMultiTab |
| `packages/react-ui-dsl/src/components/CanvasTabs/CanvasTabs.tsx` | 新增单视图条件渲染分支：不使用 `<Tabs>`，根据 activeKey 直接输出 DashboardGrid 或 PreviewContent/HTMLLoaderEmbed |
| `packages/react-ui-dsl/src/canvas/canvasStore.test.ts` | 全局 beforeEach 加 `setEnableMultiTab(true)`；新增 single-view mode describe（10 个测试） |
| `packages/react-ui-dsl/src/canvas/canvas-integration.test.ts` | PreviewCard 集成 beforeEach 加 `setEnableMultiTab(true)`；新增 single-view mode describe（3 个测试） |
| `packages/react-ui-dsl/src/index.ts` | 无改动（`CanvasStore` 类型已通过 `canvasStore.ts` 导出更新自动覆盖） |

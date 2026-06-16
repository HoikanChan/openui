# ADR-0003: Canvas Tab实现位置

## 状态

已采纳
## 背景
Canvas支持多种Tab类型：
- **DashboardTab**：DashboardCard集合的网格布局
- **PreviewTab**：PreviewCard的全页渲染
- **HTMLLoader Tab**：外部HTML的iframe嵌入（双向通信）

核心问题：Tab逻辑应在哪里实现？
## 决策
Tab功能在**前端Canvas逻辑**中实现，而非DSL层。
DSL组件仅指定：
- `DashboardCard`：`tab`属性表示DashboardTab名称（默认："Dashboard"）
- `PreviewCard`：`title`属性成为Tab名称（每卡片一个Tab）
- `HTMLLoader`：作为PreviewCard子组件，`iframeId`为通信标识，`url`为iframe源，`data`为可选通信数据

前端CanvasTabs组件：
- 按`tab`属性将DashboardCards分组到DashboardTabs
- 为每个PreviewCard创建独立的PreviewTab
- HTMLLoader作为PreviewCard子组件，通过iframeId实现双向postMessage通信
- 处理Tab渲染、切换和生命周期

实际实现采用**响应式Store模式**（`canvasStore`）：CanvasCard/PreviewCard组件在渲染时将数据推入`canvasStore`，CanvasTabs通过订阅`canvasStore`获取状态并渲染。
## 理据
### 备选方案

1. **DSL Tab组件**
   - `CanvasTab("Dashboard", [cards...])`显式分组
   - LLM必须理解Tab层级结构
   - DSL生成更复杂
   - 可能产生畸形嵌套

2. **DSL Tab声明**
   - `@CanvasTab("Preview")`标记语句
   - 破坏组件中心的DSL理念
   - LLM生成不够自然

3. **基于类型的自动分组**
   - DashboardCard → DashboardTab
   - PreviewCard → PreviewTab
   - HTMLLoader → HTMLLoader Tab
   - 无显式Tab命名能力
   - 无法支持多个DashboardTabs

### 为何选择前端逻辑

- **DSL更简单**：组件保持扁平，无嵌套复杂度
- **LLM友好**：LLM生成组件而非Tab结构
- **分组灵活**：前端可按需分组、拆分或转换
- **关注点分离**：DSL定义内容，前端定义展示结构
- **可扩展**：业务层可自定义Tab行为而不改DSL
## 影响
### 正面
- DSL保持简单扁平
- LLM直接生成DashboardCard/PreviewCard/HTMLLoader
- 前端控制Tab命名和分组逻辑
- HTMLLoader通过iframeId实现双向通信，业务层只需在iframe页面实现消息收发
### 负面
- 前端需按属性解析和分组CanvasItems
- PreviewCard和HTMLLoader总是创建新Tab（无多卡片预览Tab）
### 中性
- DashboardCard的`tab`属性支持命名的DashboardTabs
- CanvasTabs通过`canvasStore`响应式store获取数据并处理差异化
- HTMLLoader通信协议：iframe发送`{type: "openui-ready", iframeId}`表示就绪 → HTMLLoader发送`{type: "openui-data", iframeId, data}` → iframe可发送`{type: "openui-close", iframeId}`请求关闭Tab
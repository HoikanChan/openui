# ADR-0001: DSL渲染目标的Canvas组件设计

## 状态

已采纳
## 背景
OpenUI DSL需要支持将UI组件渲染到两个不同的目标：
- **LUI** (Language UI)：LLM响应的对话展示区域
- **Canvas** (Intelligent Canvas)：多Tab可视化工作区，用于仪表盘、预览和嵌入内容
核心挑战是如何标记DSL组件以指示其渲染目标。
## 决策
我们创建三个新的DSL组件，而非在现有`Card`组件上扩展`target`属性：
- **DashboardCard**：基于网格的卡片，渲染到DashboardTab（12列布局）
- **PreviewCard**：全页内容，渲染到独立的PreviewTab
- **HTMLLoader**：iframe嵌入的外部内容，渲染到独立的HTMLLoader Tab
每个组件具有不同的语义和属性：
- `DashboardCard`：`children?, title?, tab?, size?, cardId?`（默认tab="Dashboard"）
- `PreviewCard`：`children?, title!, cardId?`（title定义Tab名称，占满整个Tab）
- `HTMLLoader`：`url!, iframeId!, data?`（iframeId为通信标记，data为可选通信数据）

## 据
### 备选方案

1. **在现有Card上添加target属性**
   - 会产生歧义："这是样式容器还是画布卡片？"
   - Card已有`variant`和`width`属性；添加`target`混淆了关注点
   - 无法支持PreviewCard和HTMLLoader场景（全页、iframe）

2. **Canvas容器组件**
   - 以`CanvasCard([chart])`方式包裹子组件
   - 与选定方案类似，但Tab放置语义不够明确
   - 需要对预览和HTML场景做单独处理

3. **@Canvas内置函数**
   - `@Canvas(chart, "title")`标记语法
   - 破坏组件优先的DSL理念
   - LLM生成不够自然

### 为何选择新组件
- **语义清晰**：每个组件名称直接表达其用途
- **类型专属属性**：DashboardCard有网格尺寸，PreviewCard有全页渲染，HTMLLoader有iframeId和data
- **LLM友好**：明确的组件名称便于LLM理解和正确生成
- **Prompt可扩展**：业务层可通过`additionalRules`选择性启用/禁用各组件
## 影响
### 正面
- LUI与Canvas渲染目标清晰分离
- 每个组件可独立演进
- 业务层通过Prompt配置控制Canvas能力
### 负面
- 需维护三个新组件而非一个扩展点
- 三个组件全部启用时Prompt表面略大
### 中性
- LUI渲染完全从树中移除Canvas组件（无占位符）
- Canvas组件通过`canvasStore`响应式store传递到前端，而非原设计的`onCanvasItems`回调
- HTMLLoader通过iframeId实现双向postMessage通信：iframe发送ready后，HTMLLoader发送data；iframe可发送close请求关闭Tab
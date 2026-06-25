# 扩展开发指南：给生成式 UI 加一个自己的组件 + 工具

这份指南帮你做一件事：**让大模型能"用"你自己的业务组件，并在渲染时调用你自己的数据接口**。

你不需要改动这套生成/渲染系统本身，只要按下面的步骤"插入"一个扩展即可。全程以
`?demo=piu-extension` 这个示例为模板，照抄改名就能跑通。

> 想直接看完整可运行的样板？看这三个文件：
> - 组件 + 工具 + 扩展定义：[`src/piu-extension-demo/AlarmExtension.tsx`](../src/piu-extension-demo/AlarmExtension.tsx)
> - 把运行时注册进来的业务脚本：[`public/piu/alarm-business-piu.js`](../public/piu/alarm-business-piu.js)
> - 页面如何串起来：[`src/PiuExtensionDemo.tsx`](../src/PiuExtensionDemo.tsx)

---

## 一、先建立一个心智模型

一个扩展永远是 **两半**，缺一不可：

| | 注册到哪里 | 作用 | 谁来"看" |
| --- | --- | --- | --- |
| **生成契约**（generation） | GenUI 服务 | 告诉模型："你可以用 `AlarmSummaryCard` 这个组件、`queryAlarmSummary` 这个工具" | 大模型（进 prompt） |
| **运行时实现**（runtime） | 浏览器（通过 Piu） | 提供组件真正的 React 画法 + 工具真正的取数逻辑 | 渲染器（画到屏幕上） |

一句话：**契约让模型"知道能用什么"，运行时让浏览器"画得出、调得通"。**

> ⚠️ 最关键的一条规则：两半里的**组件名、工具名、参数顺序/字段，必须逐字一致**。
> 名字对不上，模型生成出来的东西就渲染不出来。

---

## 二、动手：6 步加一个你自己的扩展

下面用一个虚构的"订单概览卡片 `OrderSummaryCard` + 取数工具 `queryOrderSummary`"举例。
你可以直接复制示例文件改名。

### 第 0 步：复制模板

把 `src/piu-extension-demo/AlarmExtension.tsx` 复制一份成 `OrderExtension.tsx`，
把 `public/piu/alarm-business-piu.js` 复制成 `order-business-piu.js`，后面逐项改名。

### 第 1 步：定义组件（怎么画）

用 `defineComponent` + 一个 zod schema 描述组件的 props。组件就是一个普通 React 组件，
props 从 `props` 里取。

```tsx
import { defineComponent } from "@openuidev/react-ui-dsl";
import { z } from "zod";

export const OrderSummaryCard = defineComponent({
  name: "OrderSummaryCard",                 // ① 组件名（两半要一致）
  description: "订单概览卡片，展示总单量和今日新增。", // 给模型看的说明
  props: z.object({                          // ② props（两半要一致）
    title: z.string(),
    total: z.number(),
    today: z.number(),
  }),
  component: ({ props }) => (
    <section data-testid="order-summary-card">
      <h2>{props.title}</h2>
      <div>总单量 {props.total} / 今日新增 {props.today}</div>
    </section>
  ),
});
```

> 小贴士：给组件根节点加个 `data-testid`，自测和写 e2e 时能稳定选中它。

### 第 2 步：定义工具（怎么取数）

工具就是一个 `async` 函数，入参是一个对象，返回值是数据。**真实取数逻辑写在这里**
（查你自己的接口、读缓存等都行）。

```ts
export async function queryOrderSummary(args: Record<string, unknown>) {
  const region = typeof args.region === "string" ? args.region : "全国";
  // 这里换成你真实的取数；示例直接返回固定值
  return { region, total: 128, today: 9 };
}
```

### 第 3 步：组装"两半"

把组件、工具拼成两个对象：`runtimeExtension`（前端运行时）和
`generationExtension`（模型可见契约）。注意 `createLibrary(...).toSpec().components`
会自动把组件转成模型可读的契约，你不用手写 props 描述。

```ts
import { createLibrary } from "@openuidev/react-ui-dsl";

export const ORDER_EXTENSION_ID = "order-runtime-demo"; // 全局唯一 id

// 前端运行时：真正的画法 + 取数
export const runtimeExtension = {
  extensionId: ORDER_EXTENSION_ID,
  components: [OrderSummaryCard],
  tools: [
    {
      name: "queryOrderSummary",
      description: "按区域查询订单概览。",
      inputSchema: {
        type: "object",
        properties: { region: { type: "string" } },
        required: ["region"],
      },
      annotations: { readOnlyHint: true },
    },
  ],
  toolProvider: { queryOrderSummary }, // key 必须等于工具名
};

// 模型可见契约：进 prompt
export const generationExtension = {
  extensionId: ORDER_EXTENSION_ID,
  version: "1.0.0",
  components: createLibrary({ components: [OrderSummaryCard] }).toSpec().components,
  tools: runtimeExtension.tools,
  examples: [
    'orderData = Query("queryOrderSummary", {region: "全国"}, {total: 0, today: 0})\n' +
      'root = OrderSummaryCard("全国订单概览", orderData.total, orderData.today)',
  ],
  additionalRules: [
    "展示订单概览时，先调用 queryOrderSummary，再用 OrderSummaryCard 渲染结果。",
    "OrderSummaryCard 的参数顺序是 title, total, today。",
  ],
};
```

### 第 4 步：让业务脚本把运行时注册进来

运行时是通过一段**业务 Piu 脚本**注册的——它在浏览器里发一个
`smart-canvas:extend` 事件，把上面的 `runtimeExtension` 交给渲染器。
照抄 `public/piu/order-business-piu.js`，只改名字：

```js
(function () {
  const Prel = window.Prel;
  Prel.start("order-business-piu", "1.0.0", ["session", "locale"], function () {
    const extension = window.__ORDER_RUNTIME_EXTENSION__; // 页面挂上去的运行时
    const dslEngine = window.DSL_ENGINE_PIU;
    dslEngine.emit("smart-canvas:extend", extension);
  });
})();
```

页面侧只要在加载这段脚本前，把运行时挂到 `window` 上、并声明这段脚本即可
（完整写法见 `PiuExtensionDemo.tsx` 的 `bootPiuRuntime`，照抄改名）。

### 第 5 步：把契约注册到 GenUI 服务

页面启动时调用一次 `registerGeneration`，把 `generationExtension` 推给服务端。
之后所有走这个 `extensionId` 的生成请求，prompt 里就带上了你的组件和工具。

```ts
import { registerGeneration } from "./genuiService";

await registerGeneration(ORDER_EXTENSION_ID, generationExtension);
```

> 注册是**幂等替换**：同一个 `extensionId` 再注册一次，会整体覆盖上一次。
> 改了组件或规则，刷新页面重新注册即可生效。

### 第 6 步：把 prompt 规则写"够死"

这一步最容易被低估，却直接决定生成质量。`examples` 和 `additionalRules` 就是你
教模型"该怎么用"的地方。**给一个正确的范例 + 几条明确约束**，模型照着学。

实战中我们踩过的坑都来自这一步，见下一节。

---

## 三、自测清单：怎么确认通了

打开 `http://localhost:5173/?demo=piu-extension`（或你的页面），按顺序确认：

1. 左上 **Runtime** 变成 `ready`（不是 `error`）。
2. **Generation** 显示你的 `extensionId`，且 `components` / `tools` 数量和你定义的一致。
3. 点"生成"，右上能流式出现 DSL，里面**确实用到了**你的组件和 `Query("你的工具")`。
4. 预览区**画出了你的组件**。
5. 卡片上的数字 = **工具返回的真实值**（不是 DSL 里写的占位 0）。
   —— 看到真实值，就证明"模型生成 → 渲染 → 调你的工具"整条链路通了。

---

## 四、常见坑（来自真实联调）

- **两半名字对不上 → 渲染空白。**
  组件名、工具名、props 字段、参数顺序，必须逐字一致。这是 90% 的"画不出来"的原因。

- **模型复用了同一个变量名 → DSL 报错、预览空白。**
  真实联调里模型一度生成了
  `x = Query(...)` 又 `x = OrderSummaryCard(...)`，同名定义两次导致整段失效。
  解决办法是在 `additionalRules` 里**明确禁止**：
  > "每个变量名只能定义一次；接收 Query 结果的变量必须和组件变量用不同的名字。"

  这类"模型容易犯的错"，最有效的修法就是在规则里把它点名禁掉，并在 `examples`
  里给一个反面对照的正确写法。

- **只有一个组件却被包了一层容器。**
  如果你想要"直接就是这张卡"，加一条规则：
  > "只有一个组件时，直接 `root = OrderSummaryCard(...)`，不要用 Stack 等容器包裹。"

- **Query 的默认值要写占位，不要写死真实数据。**
  `Query("tool", {...}, {total: 0, today: 0})` 第三个参数是"取数前的占位"。
  真实值由你的工具在运行时返回——所以**界面上看到真实数字 = 工具真的跑了**。

- **改了规则不生效？** 规则随注册下发，**刷新页面重新注册**后才生效。

---

## 五、启动顺序速查

顺序错了会直接 `Failed to fetch`（页面 Runtime 显示 `error`）：

1. **先**启动 GenUI 服务（默认 `:3001`）。没有它，注册和生成都会失败。
2. **再**启动前端页面（默认 `:5173`）。
3. 打开 `http://localhost:5173/?demo=piu-extension`。

如果页面已经报 `error`，多半是后端没起或起晚了——把后端起好后**刷新页面**即可恢复。

---

## 一页速记

> 一个扩展 = **契约**（给模型看，注册到服务）+ **运行时**（给浏览器用，通过 Piu 注册）。
> 两半的**名字必须逐字一致**；
> 用 `examples` + `additionalRules` 把模型"教会、管死"；
> 自测就看一件事：**卡片上的数字是不是工具返回的真实值**。

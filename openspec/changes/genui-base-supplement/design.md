# GenUI Base Supplement — 技术设计

## Context

`GenerationSdk` 目前从 jar 内冻结资源 `openui/base-contract.json` 加载基础组件库（由 `packages/react-ui-dsl` 的 `generate-base-contract.mts` 导出），仅有两个扩展点：

- `builder().baseContract(...)`：整体替换，README 定位为测试后门；
- `GenUIExtension`：按 `extensionId` 逐请求叠加，与 base 同名即抛 `GenerationSdkException`，不可覆盖。

宿主业务需要「全局追加自有组件、并可整体替换 base 组件的提示词文档」。术语与决策背景见 `packages/genui-java-sdk/CONTEXT.md`（Base Contract / GenUI Base Supplement / Effective Base Contract / GenUI Extension 四条词条）。

约束：无增补包时 prompt 输出必须与现状字节级一致（`PromptGoldenTest` 是跨语言对齐的锚点，不可破坏）。

## Goals / Non-Goals

**Goals:**

- 宿主以一份 JSON 在构建期全局增补基础组件库：组件追加/同名整体替换、group 并集合并、examples 与 additionalRules 追加。
- 合并在 `GenerationSdk` 构建期一次完成，产出 Effective Base Contract；下游代码路径（prompt 组装、extension 碰撞校验）不感知分层。
- 构建期完成全部校验，错误信息可定位到增补包。

**Non-Goals:**

- 不支持删除/禁用 base 组件。
- 不支持修改 `tools` / `root` / `builtins` / `contractVersion`。
- 不提供程序化拼装 API（JSON 是唯一受支持的输入形态）。
- 不提供增补包 JSON 的生产工具（前端导出 helper 不在本次范围，脱节风险由宿主承担）。
- 不上报增补包版本到 `GenUIPromptAssemblyMetadata`。
- 单实例仅支持一份增补包；多团队合包由宿主预处理。

## Decisions

### D1. JSON 格式：base-contract 的同构子集 + 严格顶层键校验

```jsonc
{
  // 追加或整体替换（同名时 description 与 propsSchema 一起换）。
  // 组件条目格式与 base-contract.json 完全一致，两种形态都支持：
  //   { "description": "...", "propsSchema": { ... } }   // schema 形态
  //   { "signature": "...", "description": "..." }        // 签名形态
  "components": {
    "TopoChart": {
      "description": "网络拓扑图。nodes/links 绑定 host data。",
      "propsSchema": {
        "type": "object",
        "properties": {
          "nodes": { "type": "array", "items": { "type": "any" } },
          "links": { "type": "array", "items": { "type": "any" } }
        },
        "required": ["nodes", "links"]
      }
    }
  },

  // 同名 group：components 并集（保序去重），notes 追加；新 group 追加到末尾。
  "componentGroups": [
    { "name": "Charts", "components": ["TopoChart"], "notes": ["拓扑关系数据优先 TopoChart"] }
  ],

  // 均为追加。
  "examples": ["root = Stack([topo])\ntopo = TopoChart(data.nodes, data.links)"],
  "additionalRules": ["涉及网元连接关系时优先使用 TopoChart 而非 Table"]
}
```

**顶层键严格校验**：允许的键仅 `components` / `componentGroups` / `examples` / `additionalRules`，全部选填。出现其他键（包括 `tools`、`root`、`builtins`、`contractVersion`）→ 加载即抛异常，消息列出非法键。

- 为什么严格而不是像 `GenerationContractLoader` 那样宽容忽略：base-contract.json 是机器导出的，宽容无害；增补包按本次决策由宿主**手工生产**，静默忽略拼错的键（如 `additionalRule` 少个 s）会让宿主误以为生效了，排查成本远高于一次报错。
- 备选（拒绝）：宽容忽略未知键 —— 与「宿主自行生产 JSON」的边界组合后风险过高。

### D2. Java API：一个 record + 一个 loader + 一个 builder 入口

```java
// 新增：com.huawei.cloudsop.genui.core.contract.GenUIBaseSupplement
public record GenUIBaseSupplement(
        Map<String, ComponentPromptSpec> components,   // 可为空
        List<ComponentGroup> componentGroups,
        List<String> examples,
        List<String> additionalRules) {
    // 紧凑构造器做防御性拷贝（与 GenUIExtension 相同风格）
}

// 新增：com.huawei.cloudsop.genui.core.contract.GenUIBaseSupplementLoader
public final class GenUIBaseSupplementLoader {
    public static GenUIBaseSupplement fromJson(String json);       // 唯一解析入口，含 D1 严格校验
    public static GenUIBaseSupplement fromResource(String path);   // classpath 便捷方法，内部走 fromJson
}

// GenerationSdk.Builder 新增：
public Builder baseSupplement(GenUIBaseSupplement supplement);
```

宿主用法：

```java
GenerationSdk sdk = GenerationSdk.builder()
        .baseSupplement(GenUIBaseSupplementLoader.fromResource("genui/company-supplement.json"))
        .build();
```

- 为什么 builder 收 record 而不是 JSON 字符串：与现有 `baseContract(GenerationContract)` + `GenerationContractLoader` 的「loader 解析、builder 装配」分工一致；解析错误在 loader 处抛出，语义清晰。
- record 构造器天然 public（Java 语言限制），无法只留 JSON 入口；以 Javadoc 声明「loader 是唯一受支持的构造路径，直接 new 仅限测试」。
- 组件条目解析复用 `GenerationContractLoader` 现有的 `componentMap` / `componentGroups` / `strings` 私有方法（提为包内可见），保证两种组件形态、group 结构的解析与 base contract 永远一致。

### D3. 合并时机与产物：构建期合并为普通 GenerationContract

合并逻辑收敛在一个包私有静态方法（放在 `GenUIBaseSupplement` 内，如 `GenerationContract applyTo(GenerationContract base)`），`GenerationSdk` 私有构造器在现有校验前调用一次。产物就是普通的 `GenerationContract`（Effective Base Contract），存入现有 `baseContract` 字段——**`assemblePrompt`、`register` 及其碰撞校验零改动**。

合并规则（对应 CONTEXT.md 词条定义）：

| section | 规则 | 顺序语义 |
|---|---|---|
| `components` | 同名整体替换，新名追加 | `LinkedHashMap.putAll`：被替换项保持 base 原位置，新增项按增补包顺序排在末尾 |
| `componentGroups` | 同名 group：components 并集（保序去重，base 成员在前）、notes 追加；新 group 追加 | base group 顺序不变，新 group 按增补包顺序排在末尾 |
| `examples` / `additionalRules` | 追加 | base 在前，增补包在后 |
| `contractVersion` / `root` / `tools` / `builtins` | 原样保留 base | — |

- 为什么不引入新类型（如 `EffectiveBaseContract`）：下游只需要一个 `GenerationContract`；分层信息在构建完成后没有消费方（版本不上报），引入新类型徒增概念。
- 为什么不复用 `GenUIExtension` 作载体：它的语义是 per-request、append-only、带 extensionId/tools；强行复用会让「同一个类型在两处有相反的碰撞语义」，违背术语唯一性。

### D4. 校验分两级，错误可归因

1. **加载期**（`fromJson`）：JSON 顶层键白名单（D1）；结构非法（components 不是对象等）沿用 `Json.asObject/asList` 的现有报错。
2. **构建期**（`GenerationSdk` 构造器）：
   - 先以 scope `"base supplement"` 对增补包自身组件跑 `validateComponents`（propsSchema 合法性），让错误消息直指增补包而非合并结果；
   - 合并后沿用现有对 base contract 的 `validateComponents` + `validateComponentGroups`（group 引用的组件必须存在于 Effective Base Contract——增补包 group 引用了不存在的组件在此报错）；
   - `register(GenUIExtension)` 不改：碰撞检查天然针对 Effective Base Contract，即 extension 不得与增补包追加的组件同名。

### D5. Golden 兼容性策略

- 不传 `baseSupplement` → `applyTo` 不被调用，`baseContract` 字段与现状完全相同，`PromptGoldenTest` 字节级不变。
- 不为增补包新增跨语言 golden（TypeScript 侧没有对应 oracle，增补包是 Java 独有能力）；用 Java 单测锁合并语义与含增补包的 prompt 输出快照。

## Risks / Trade-offs

- [宿主手写 JSON 与其前端渲染能力脱节，模型生成了前端渲染不了的组件] → 明确为宿主责任；README 增补包章节显著提示「每个组件必须已在宿主前端注册」，并建议宿主用其前端注册表导出（本次不提供工具）。
- [SDK 升级 base contract 后，被整体替换的组件停留在宿主的旧 spec] → 整体替换是有意选择（规则可预测）；README 提示升级 SDK 时 review 增补包中与 base 同名的条目。
- [group 并集无法移除 base 组内成员、组件替换无法删除组件] → 已知边界（无删除能力），文档写明；真有需求时走前端重新导出 base contract 的既有链路。
- [组件替换与 group 并集是两套合并规则，易记混] → 集中在 README 一张表 + `GenUIBaseSupplement` Javadoc 重复说明；错误消息中不使用「merge」一词描述组件行为。

## Open Questions

- 无。命名、粒度、范围、形态、数量、边界、版本策略均已在前期评审中敲定（见 CONTEXT.md 与 proposal）。

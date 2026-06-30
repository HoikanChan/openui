## Context

`PromptAssembler.dataModelSection`(Java)与 `prompt.ts` 的 `dataModelSection`(TS oracle)当前都把整份 host data 用 pretty JSON 直接拼进 `## Data Model`,两侧由 `prompt-golden/03-data-model` fixture 钉死做字节对齐。

**决定整套机制的关键事实**:同一份 `request.response()` 在 `GenUiGenerator` 中走两条**互相解耦**的路径——
- 作为 seq=0 `dataModel` envelope 下发(`RenderStreamEnvelope.dataModel(effectiveRequest.response())`)并由 `GenUiGenerationResult` 原样回传,**UI 实际渲染绑定的是这一份全量数据**(Render Data Model);
- 经 `toPromptRequest` 包装成 `DataModelSpec(request.response())`,再由 `assemblePrompt` 拼进 prompt,**仅供模型理解形状**(Prompt Data Model)。

两条路径在代码里只共享 `response()` 这一个只读引用,互不写回。术语与不变量见 `packages/genui-java-sdk/CONTEXT.md`。

**现状约束**:
- `Json`(fastjson2)解析后,数字归一为 `Long`/`Double`,对象为有序 `LinkedHashMap`,数组为 `ArrayList`;`Json.stringify` / `Json.stringifyPretty` 已有。
- `DataModelSpec(String description, Map<String,Object> raw)` 是不可变 record。
- prompt 组装的唯一收敛点是 `GenerationSdk.assemblePrompt(GenUIPromptRequest)`,它把 `request.dataModel()` 塞进 `PromptAssembler.PromptInput`。

## Goals / Non-Goals

**Goals:**
- 在不损失渲染效果前提下,把超大 Prompt Data Model 压缩进 token 预算。
- 保持 `data.<field>` 引用路径有效;保证枚举域完整(不漏 `@Switch` case)。
- 确定性、无 LLM、单遍、有界,可被 golden/缓存依赖。
- **跨语言字节一致仅在压缩未触发时要求**:小数据/禁用路径与 TS oracle 逐字节一致(golden 覆盖此路径);压缩生效时 prompt 可自由发散(TS 侧无对应压缩,无须一致),实现不被此约束。
- 交付可度量的效果验证(压缩比 + 形状保真 + 端到端无回归)。

**Non-Goals:**
- 不做可逆压缩;v1 不做全局 token 预算与跨字段配额;v1 不输出数值统计(min/max/avg);不做图表保真降采样;不改 Render Data Model。

---

## Architecture:模块布局与职责

新增包 `com.huawei.cloudsop.genui.core.prompt.characterize`:

| 类型 | 职责 |
|---|---|
| `Characterizer`(入口) | `static DataModelSpec characterize(DataModelSpec in, CharacterizationConfig cfg)`;体积闸门 → 调 walker → 拼装新 `DataModelSpec` |
| `CharacterizationConfig`(record + builder) | 全部阈值 + 默认值;`enabled` 开关 |
| `ShapeWalker` | 递归核心,单遍产出 `Characterized{Object sample, ShapeNode shape}` |
| `ShapeNode`(sealed interface) | 推断出的 schema 树:`ObjectShape` / `ArrayShape` / `ScalarShape` / `EnumShape` |
| `ColumnAccumulator` | 对象数组逐列累积:类型集合、容量上限 distinct 集、出现数/null 数 |
| `TsTypeRenderer` | `ShapeNode → TypeScript 类型文本`(sidecar 正文) |

`ShapeNode` 草模:
```
sealed interface ShapeNode
record ObjectShape(LinkedHashMap<String, FieldShape> fields)            // FieldShape{ShapeNode node, boolean optional, boolean nullable}
record ArrayShape(ShapeNode element, long count, boolean truncated)
record ScalarShape(ScalarType type)            // STRING/NUMBER/BOOLEAN/NULL/UNKNOWN
record EnumShape(List<String> domain)          // 完整枚举域,确定性排序
```

`CharacterizationConfig` 默认值:
```
enabled        = true
triggerBytes   = 2048      // raw 序列化体积闸门
sampleRows K   = 3
maxStringLen   = 80
enumMaxDistinct= 50        // 绝对基数上限
enumMaxRatio   = 0.5       // distinct/total
deepScanLimit  = 10000     // 嵌套对象列的深度递归扫描上限(枚举/计数本身不受此限,见下)
```

---

## 内部机制:walker 算法

`Characterizer.characterize` 流程:
1. **闸门**:`in == null || raw 为空` → 原样返回;`Json.stringify(raw).length() <= triggerBytes || !enabled` → 原样返回(小数据零行为变化,golden 不受影响)。
2. **遍历**:`walk(raw, cfg, depth=0)` 得 `{sample, shape}`。
3. **拼装**:`description' = 原 description + "\n\n" + TsTypeRenderer.render(shape)`;返回 `new DataModelSpec(description', sample)`。

`walk(value, cfg, depth)` 按运行时类型分派:

- **null** → `{null, ScalarShape(NULL)}`
- **String s** → `{ s.length()>maxStringLen ? s.substring(0,maxStringLen)+"…" : s, ScalarShape(STRING) }`
- **Long/Double** → `{ value, ScalarShape(NUMBER) }`
- **Boolean** → `{ value, ScalarShape(BOOLEAN) }`
- **Map** → `walkObject`
- **List** → `walkArray`

**walkObject(map)**:有序遍历每个 entry,递归 `walk` 子值;`sample` 为保留全部键的 `LinkedHashMap`(v1 不丢键);`shape` 为 `ObjectShape`,各字段 `optional=false`(对象语境下键恒存在)。

**walkArray(list)**——两趟有界扫描:

- 趟 A(**样本**,只取前 K):对 `list[0..min(K,n))` 各 `walk`,得 `sample`(截断后的前 K 个元素)。
- 趟 B(**特征**,全量但廉价):依首元素判定数组族:
  - **对象数组(表格)**:对**全部** n 个元素,逐列喂 `ColumnAccumulator`——累积该列出现的标量类型集合、容量上限 distinct 集(`HashSet` 超 `enumMaxDistinct+1` 即停止增长并打高基数标记)、出现数与 null 数。嵌套对象/数组列的**深层** schema 仅对前 `deepScanLimit` 个元素递归推断。收尾对每列定形:
    - 字符串列:`distinct ≤ enumMaxDistinct 且 distinct/total ≤ enumMaxRatio` → `EnumShape(sorted domain)`;否则 `ScalarShape(STRING)`。
    - 数字/布尔列:对应 `ScalarShape`;多标量类型混合 → `ScalarShape(UNKNOWN)`(渲染为 `unknown`)。
    - `null 数>0` → 该字段 `nullable=true`;`出现数<n` → `optional=true`。
    - 元素 shape = 各列 `FieldShape` 组成的 `ObjectShape`。
  - **数字数组(序列)**:元素 shape = `ScalarShape(NUMBER)`(不做降采样)。
  - **字符串数组**:整体按一列做枚举判定(同上)。
  - **异构/嵌套数组**:元素 shape 取首元素 shape 或 `UNKNOWN`,v1 不深究。
- `shape = ArrayShape(element, count=n, truncated = n>K)`。

**完整枚举域 vs 性能的取舍**(关键):枚举/计数趟 B 对**全部**元素跑,但每列 distinct 用容量上限集合——一旦越过基数上限即停止收集并降级为自由文本。因此"列是枚举"⇔"基数在上限内",此时其 distinct 集**必然完整**,满足 spec 的"枚举域完整"。`deepScanLimit` 只约束**嵌套对象列**的深层 schema 推断,不约束顶层枚举/计数,故不损害顶层枚举完整性。开销为 O(n) 短字符串哈希,prompt 规模(数千~数百万行)在毫秒级。

**确定性**:样本恒取输入前 K 个;枚举域按**字典序**排序后输出(对上游行序重排稳健);Map 保持输入键序。同输入两次产出逐字节相同。

---

## Sidecar 渲染:TS-type 语法

`TsTypeRenderer` 把根 `ShapeNode` 渲染为以 `data` 为根的 TypeScript 类型,顶层用 `data` 强化引用路径直觉:

```ts
data: {
  title: string
  rows: {
    id: number
    name: string
    status: "open" | "closed" | "pending"
    revenue: number
    note?: string | null
  }[]  // 10000 items (showing 3)
}
```

规则:
- `EnumShape` → union 字面量 `"a" | "b" | "c"`,与 `@Switch(x, {"a":…})` 同构。
- `ArrayShape(truncated)` → 元素类型后缀 `[]` + 注释 `// <count> items (showing <K>)`;未截断不加注释。
- `optional` → `field?`;`nullable` → `| null`。
- 标量 → `string`/`number`/`boolean`/`unknown`。

**sidecar 落点(松绑后)**:既然压缩生效时不要求与 oracle 一致,sidecar **不必**硬塞 `description`,可取最佳形态——推荐让 `dataModelSection` 在"检测到 characterization 元数据"时走加强分支:先 JSON 样本块、再追加 `Data shape (full dataset):` + fenced ```ts 块(样本在前、完整 schema 在后,顺序更自然)。该分支仅在压缩生效时进入,与 TS oracle 的发散是被允许的;未压缩时该方法体逐字节不变,golden 仍绿。
- 实现选项(任选其一,不被 parity 限制):(a)给 `DataModelSpec` 加可空 `shapeSidecar` 字段,`dataModelSection` 有值则追加;(b)沿用 `description` 携带(最省事,但排序次优)。**推荐 (a)**,排序更优、语义干净。

---

## 外部模块对接工作流

**接入点选择:`GenerationSdk.assemblePrompt`(唯一 prompt 收敛点)**,而非 `GenUiGenerator.toPromptRequest`。
- 理由:`assemblePrompt` 同时覆盖 `GenUiGenerator` 路径与**直接调用 `assemblePrompt` 的 SDK 使用方**,一处接入全覆盖。
- 改动:`GenerationSdk` 持有 `CharacterizationConfig`(经 `Builder.characterization(cfg)` 注入,默认 `defaults()`);在 `assemblePrompt` 构造 `PromptInput` 前:
  ```
  DataModelSpec dm = Characterizer.characterize(effectiveRequest.dataModel(), characterization);
  // …PromptInput 中以 dm 取代 effectiveRequest.dataModel()
  ```

**generate(同步)调用时序**:
```
caller → GenUiGenerator.generate(req)
       → buildRequestBody → toPromptRequest:  DataModelSpec(full response)        // 全量
       → sdk.assemblePrompt(promptReq)
            → Characterizer.characterize(dataModel)  → 采样后 DataModelSpec        // 仅此处瘦身
            → PromptAssembler.assemble(…采样 dm…)     → prompt 文本
       → transport.post → LLM
       → return GenUiGenerationResult(dsl, effectiveRequest.response())            // 回传仍全量
```

**generateStream(流式)时序**:`sink.accept(RenderStreamEnvelope.dataModel(effectiveRequest.response()))` 在 `buildRequestBody` **之前**发出 seq=0 全量 envelope;随后 `buildRequestBody → assemblePrompt → characterize` 只影响 prompt。**全量数据在采样发生前已下发,不变量天然成立。**

**配置贯通**:`GenUiGenerator` 内部自建 `GenerationSdk.create()`(默认配置)。为让生成方可调采样参数,新增 `GenUiGenerator.create(GenUiLlmConfig, CharacterizationConfig)` 重载(或 builder),内部转 `GenerationSdk.builder().characterization(cfg).build()`。默认开启,`enabled=false` 即整体回退原全量行为(快速 rollback)。

**对接校验点**:接入后须断言 seq=0 envelope 与 `GenUiGenerationResult.dataModel()` 仍等于全量 `response()`,未被 `characterize` 触及。

---

## 边界情形

- **空/小数据**:闸门拦截,原样返回。
- **稀疏列**(部分行缺键):`optional → field?`。
- **含 null 的列**:`nullable → | null`。
- **混合标量列**(数字 + 字符串):`unknown`,不强行 union。
- **数组套数组 / 深层嵌套**:递归同规则;深层 schema 受 `deepScanLimit` 约束,深度过大时尾部以 `unknown` 收口。
- **超大基数字符串列**:容量上限集合提前终止 → `string` 自由文本。
- **顶层即数组**(`raw` 本身非对象):`DataModelSpec.raw` 约束为 `Map`,此情形由上游保证,walker 仍可处理 List 值。
- **非有限数 / 特殊数值**:沿用 `Json` 既有归一(`stringify` 已处理 NaN/Inf→null)。

## 性能与复杂度

- 时间 O(n)(n=节点总数):趟 A 仅 K 个元素深递归;趟 B 全量但每元素仅做列累积(哈希 + 计数)。
- 内存有界:每列 distinct 集封顶 `enumMaxDistinct+1`;样本仅 K 行。
- 无第三方依赖;复用现有 `Json`。

---

## Decisions(取舍汇总)

- **D1 组装前纯变换、接入 `assemblePrompt`**:全收敛点覆盖。备选"接 `toPromptRequest`"否决(漏掉直接 assemblePrompt 使用方)。注:`dataModelSection` 可在压缩生效分支内自由发散(parity 仅约束未压缩路径)。
- **D2 同构树 + out-of-band sidecar**:往树内注入 `__count__` 会让模型写出 `data.rows.__sample__`、断引用,故刻画信息一律 out-of-band。
- **D3 TS-type sidecar(union 与 `@Switch` 同构)**:落点推荐给 `DataModelSpec` 加可空 `shapeSidecar` 字段、由 `dataModelSection` 加强分支在样本块后追加(样本在前、schema 在后)。`description` 携带为备选。**parity 松绑后不再被"零改 assembler"约束**,取排序与语义最优解。
- **D4 自研单遍 walker、无依赖**:`json-schema-inferrer` 不产枚举/count/样本且输出 JSON Schema,净收益为负。
- **D5 阈值借鉴社区实证**:K=3(LIDA);枚举 `≤50 且 ≤0.5`(ydata-profiling/pandas);触发 >2KB。
- **D6 效果验证为一等交付物**:压缩比 + 形状保真断言 + 端到端不漏 case。

## Risks / Trade-offs

- [采样致选错组件/漏枚举] → sidecar 强制带 count(影响组件选择)与**全量扫描**得到的完整枚举域;D6 端到端量化兜底。
- [`deepScanLimit` 致超深嵌套 schema 不全] → 仅影响嵌套对象列的深层类型,顶层枚举/计数完整;尾部以 `unknown` 显式收口,不误导。
- [压缩生效时 Java prompt 与 TS oracle 发散] → **设计内允许**:TS 侧无对应压缩,无一致性可言;parity 仅约束未压缩路径,golden 通过让 fixture 不触发阈值(或禁用)保证(见 Migration)。

## Migration Plan

- 默认开启但阈值化触发:`raw < 2KB` 原样通过,既有小数据 prompt 与行为不变,golden 全绿。
- **golden 保证方式**:`prompt-golden` fixture 保持在触发阈值以下(或在 golden 测试中禁用 characterization),从而只校验"未压缩 == TS oracle"这一被要求一致的路径;压缩生效的发散不进 golden,改由 D6 形状/端到端验证覆盖。
- `CharacterizationConfig.enabled=false` 为快速 rollback。
- 分阶段:walker + sidecar + 单测 → D6 度量与端到端 → 依度量标定阈值。

## Open Questions

- 触发阈值与压缩比目标的具体数值,由 D6 在真实代表性数据集标定后回填(当前 2KB/K=3 为初值)。
- 数值统计(min/max/avg)是否最终需要,取决于端到端是否暴露"缺统计致选错组件"。
- 超深/数组套数组是否需要显式深度上限策略,视病理输入再定。

### Resolved (2026-06-27)

- **效果验证已交付**:`CharacterizationEffectTest` 在三个代表性 fixture 上测得压缩比 —
  A(1万行宽表)=0.05%、B(1万点数值序列)=0.06%、C(深层嵌套对象)=0.77% —
  均优于 spec 7.2 的保守下限(A/B ≤10%,C <100% 且 ≤50%),详见
  `target/characterization-effect-metrics.md`。枚举域完整性与真实计数也由同一测试类
  (7.3 节断言)独立校验通过。
- **初始阈值保留为默认值**:`triggerBytes=2048`、`K=3` 不改动,作为 v1 默认值随
  `CharacterizationConfig.defaults()` 发布。在真实代表性生产数据集上的标定,以及任何
  需要真实 LLM 的"漏 case"研究,**推迟**——当前构建环境没有可用的真实 LLM,7.4 节的
  "端到端"验证只能是 prompt 内容代理(断言完整枚举域 union 与真实 array count 注释
  确实进入了组装后的系统提示词),并非对真实模型输出的断言。
- **数值统计(min/max/avg)继续是 v1 的 Non-Goal**:除非未来端到端研究证明缺少这些
  统计会导致组件被错误选择,否则不在本特性范围内新增。

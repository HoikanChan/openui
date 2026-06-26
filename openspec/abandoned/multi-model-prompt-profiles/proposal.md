> Superseded: This change is abandoned in favor of `prompt-hardening-for-model-migration`.
>
> Reason: the earlier scope centered on a broad model-profile/prompt-management system. The replacement change narrows the problem back to the ds-flash/deepseek -> qwen3 migration regression and solves it through shared prompt hardening, thin model invocation options, compact examples, and one-shot parse repair.

## Why

`@cloudsop/openui-react-ui-dsl` 的 system prompt 是历经多轮在 deepseek-v3 上调优堆出来的：800-1000 行的 grammar/builtins/components 描述 + 46 条 `additionalRules` + 19 条静态 few-shot examples，全部无差别地喂给所有模型。

切换到生产要求的 `qwen3-30b-a3b-instruct-2507` 后，这种"single-prompt-for-all-models"的形态立刻暴露问题：

- bench 上 4/44 直接 parse 失败（bracket 不平衡、调用 JS API `data.x.toString()`、自定义伪组件模板、组件 arity 错位等）
- judge 分数 component_fit / data_completeness 多项掉分
- 失败的多数 case **规则文档里其实早写过禁用**，但被深埋在 prompt 中段，qwen3 的指令遵循衰减比 deepseek 严重

不只是当前一次切换的问题：长期还要再切换、再适配新模型，需要架构上能区分"语言事实 / 渲染策略 / 模型反模式"三层的能力，并具备能定量驱动 prompt 维护的工具链。

## What Changes

- **新增** model profile 抽象：`packages/lang-core/src/profiles/<model>.{yaml,ts}`，profile 持有 model 特有的 anti-patterns、example 选择策略、section 排版偏好、verbosity、sampling 参数等；多 profile 走"base + override 继承"模式
- **重构** `prompt.ts` 与 `dslLibrary.tsx` 的内容分层：
  - **L1（语言事实）**：grammar / builtins / 组件签名 / Query / Mutation / Action / streaming，保留为 model-agnostic 的 base prompt，**所有模型共享**
  - **L2（渲染策略）**：从现 46 条 `additionalRules` 中析出"任何模型都该遵循的产品级渲染准则"，独立到 `corePolicy.ts`
  - **L3（模型反模式）**：剩余规则按消融结果迁移到对应 model profile
- **新增** anti-pattern 强制注入两个高 attention 槽（"syntax rules 之后" + "final verification 之前"），缓解 lost-in-the-middle 衰减
- **重构** examples：
  - 从 `dslLibrary.tsx` 的硬编码数组迁移到 `examples/` 子目录，每条例子带 `tags: string[]`
  - 实现一个轻量 `classifyDataShape(data) → tags[]` 代码 classifier（无 embedding）
  - 推理时按 tag overlap 取 top N（默认 5-8 条），可由 profile 覆盖
  - 把适合的"never do X"反模式条目改写为对应的"do Y"正例并打 tag
- **新增** self-repair 重试循环：parser 报错时把 `failureReason` 截断回喂模型，让其修一次（实测 Mermaid 等场景 80%+ 一轮可恢复）
- **新增** ablation harness：`pnpm eval ablate --rule <id> [--model <name>]`，临时移除规则、跑 bench、给出 delta；分 train（e2e）/ holdout（benchmark/fuzz）避免过拟合；用于一次性盘点 46 条 rule 的归属
- **新增** prompt 长度软上限（`maxPromptChars`），构建期 lint 兜底反 bloat
- **新增** Qwen3 官方推荐 sampling profile：`temperature=0.7, topP=0.8, topK=20, minP=0, enable_thinking=false`，由 profile 同时持有
- **不改** lang-core parser 与 DSL 语法本身（语法层面的"末尾可选 positional 参数过多"问题作为单独后续 change 提出）
- **不上** XGrammar / vLLM `guided_grammar` 约束解码（vLLM 在 `Instruct-2507` 上的 guided decoding 当前有 bug；语义类失败 grammar 也救不了；先把 prompt 层做扎实，grammar 留作后续根治层）
- **不引入** BAML / DSPy / Guidance 等第三方 prompt 框架（现有 sectioned `prompt.ts` 已有同等抽象级别）
- **不上** embedding-based example retrieval（input 形状是离散有限集，规则匹配天然碾压）

## Capabilities

### New

- `model-profile`：每个模型一份配置，声明 anti-patterns / example 策略 / section 排版 / sampling，多 profile 继承
- `prompt-layered-content`：L1/L2/L3 三层分离，base prompt + corePolicy + profile 三段独立维护
- `prompt-attention-aware-layout`：anti-patterns 强制注入头尾两个高 attention 槽
- `example-dynamic-selection`：根据 dataModel 形状 + profile 策略动态挑 examples
- `data-shape-classifier`：纯代码 classifier，把 dataModel 映射到 tags 集合
- `prompt-self-repair`：parse 失败时回喂错误信息让模型一次重试
- `prompt-ablation-harness`：定量度量每条规则对每个模型的边际贡献
- `prompt-bloat-lint`：长度软上限，build 期警告

### Existing capability changes

- `dslLibrary.prompt(options)` API 扩展 `model` 字段；不传则使用 `default` profile
- `eval` CLI 新增 `ablate` 子命令
- `regen` / `eval` 流程自动应用 profile 中的 sampling 参数

## Impact

- `packages/lang-core/src/parser/prompt.ts` — section 排版重排；接受 profile；anti-pattern 槽位机制
- `packages/lang-core/src/profiles/` — 新增目录与多份 profile 文件
- `packages/lang-core/src/index.ts` — 暴露 profile 类型
- `packages/react-lang/src/library.ts` — `PromptOptions` 透传 `model`
- `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` — 拆 `DEFAULT_PROMPT_ADDITIONAL_RULES` 到 `corePolicy.ts` 与各 profile；examples 迁出
- `packages/react-ui-dsl/src/genui-lib/corePolicy.ts` — 新增，托管 L2 规则
- `packages/react-ui-dsl/src/genui-lib/examples/` — 新增目录，托管 tagged examples 与 `classifyDataShape`
- `packages/react-ui-dsl/src/__tests__/e2e/eval/` — 新增 ablate 命令与相关辅助
- `packages/react-ui-dsl/src/__tests__/e2e/llm.ts` — 接入 self-repair retry
- 调用方（regen / eval / 生产 runtime）需要传 `model` 参数；缺省走 `default` profile，向后兼容

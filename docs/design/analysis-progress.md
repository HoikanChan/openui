# validation 架构分析进度

## 当前状态

阶段一已完成，当前文档已经覆盖：

- `validation` 的总体职责和外部边界。
- 宏观 4+1 架构视图。
- 一次性校验和流式校验的主流程。
- 六个逻辑模块及其责任边界。
- 模块之间的执行关系和共享能力。
- 流式轮次状态和增量修复入口。

阶段二已完成模块 2、3、4、6 的详细设计说明。模块 1 和模块 5 尚未展开。

## 当前模块划分

1. 公共 API 与编排。
2. 语法解析与程序模型。
3. 语义与契约分析。
4. 静态类型与数据分析。
5. 流式程序状态。
6. 增量修复提示。

`BuiltinAnalysis` 作为共享 builtin 语义内核处理，不单独算作第七个模块。它同时服务语义分析、类型分析和契约加载。

## 已确认的架构结论

- `OpenuiLangValidator` 是一次校验的公共入口。
- `DefaultOpenuiLangValidator` 负责串联快照、解析、语义分析、类型分析和结果合并。
- `Program` 是语法分析、语义分析和类型分析之间的共同程序模型。
- `ProgramRoundSession` 保存跨轮次的当前定义，轮末才触发统一校验。
- `GenUiGenerator` 负责模型调用、增量修复循环和失败降级。
- `RepairPromptBuilder` 只生成消息，不调用模型。
- 通用 `UNPROVEN` 主要进入覆盖报告。数据路径无法解析等场景有独立的错误政策。

## 阶段二产出

- 模块 3：`03-semantic-contract-design.md`
- 模块 6：`06-incremental-repair-prompt-design.md`
- 模块 2：`02-parser-program-design.md`
- 模块 4：`04-type-data-analysis-design.md`

## 后续工作

- 补充模块 1“公共 API 与编排”的详细设计。
- 补充模块 5“流式程序状态”的详细设计。
- 在四个视图的基础上整合完整的 `02-architecture-views.md`。
- 完成跨模块架构审查和 `03-architecture-review.md`。

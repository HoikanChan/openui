# validation-iteration 分支说明

> **本分支是外部迭代线，不合并回 master。** master 保留完整的前端包与前端相关 workflow。

本分支按 [repair-bench 设计](superpowers/specs/2026-10-13-repair-bench-design.md)收缩，只保留 validation / repair 迭代所需的代码：

- `packages/genui-java-sdk` — validation + repair 主战场（parser / semantic / stream / repair）
- `packages/genui-eval-cli` — 生成与修复基准 CLI（repair-bench 扩展点）
- `packages/lang-core` — 框架无关 Lang 内核，Java parser 的跨语言对齐 oracle 来源
- `examples/genui-service` — SmartCanvasService 契约参考实现
- `interal-code/` — 内部参照代码
- `reports/` — 既有 Reask 修复率根因分析等迭代输入

已删除：react-ui / react-ui-dsl / react-lang / react-headless / openui-cli 前端包、前端演示 examples、对应 skills 与 JS 构建/发布 workflows。删除原因：内部前端已大改，外部前端代码对内部错误数据不可用，且过期代码会误导迭代（详见设计文档「前置判断 1」）。

根 README.md 描述的是完整上游仓库，与本分支形态不同——以本文件为准。

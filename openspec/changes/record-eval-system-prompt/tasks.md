## 1. Prompt Artifact Helper

- [ ] 1.1 新增或扩展 eval prompt helper，使用 `dslLibrary.prompt()` 和占位 data model 生成 canonical system prompt
- [ ] 1.2 为 canonical system prompt 计算基于完整 UTF-8 内容的 sha256 hash
- [ ] 1.3 添加 helper 单元测试，证明 strictness 会影响 prompt 内容/hash，且占位 data model 不包含 fixture 数据

## 2. Run Manifest Integration

- [ ] 2.1 扩展 `RunManifest` 类型，记录 system prompt artifact 的 run-relative path 和 hash
- [ ] 2.2 在 run workspace 创建后写入 `system-prompt.txt`，并将 path/hash 写入 manifest
- [ ] 2.3 确保 regen 失败或后续 phase 中断时，已创建的 prompt artifact 仍保留在 run 目录中

## 3. Report Metadata Integration

- [ ] 3.1 扩展 report data 类型，记录 canonical system prompt path/hash
- [ ] 3.2 在 report 生成和增量写回路径中保留 prompt metadata
- [ ] 3.3 更新 report 相关测试，确认 report metadata 与 run manifest 一致

## 4. CLI Visibility

- [ ] 4.1 更新 `pnpm eval status <run-id>` 输出，显示 prompt artifact path 和 hash
- [ ] 4.2 更新 eval CLI usage 或相关说明，提示 run 目录中保存 canonical system prompt

## 5. Verification

- [ ] 5.1 运行 prompt helper、run manifest、report metadata 相关 unit tests
- [ ] 5.2 运行一次最小 eval start/regen dry path 或等价测试，确认 run 目录生成 `system-prompt.txt`
- [ ] 5.3 检查生成的 prompt 中包含组件签名、builtins、standard/strict rules，并且 data model section 是占位符

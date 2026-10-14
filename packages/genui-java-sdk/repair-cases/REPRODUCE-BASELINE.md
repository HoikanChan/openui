# REPRODUCE-BASELINE — validation 首跑与 md 根因记录对比

- **生成**: 2026-10-14(f6d30fdc)
- **跑法**: `RepairBenchCli --cases=repair-cases/`(validation-only,零 LLM)
- **契约**: 语料级 `contract.json` = base-contract 1.1.5(`@cloudsop/openui-react-ui-dsl@1.1.5`)
- **builtin 注册表状态**: `Builtins.java` @ c56a37ae —— 静态硬编码,与 1.1.5 契约 27 个 builtin **错位**:注册表缺 `Flatten / ObjectValues / Number / String / Join / MergeObject`(契约有、语料 DSL 大量使用),多 `FormatNumber / FormatPercent`(契约没有)。#42(契约驱动注册表)在另一条线修复中。
- **dataModel 状态**: 25 例中 7 例缺失(见 CONVERSION-GAPS.md),类型类根因不触发,首跑记 `not-reproduced`。

**结论速览**: 25 例里 18 例复现(INVALID),7 例 not-reproduced(全部是缺 dataModel 的——**不是契约漂移,是语料缺口**,回填后即恢复)。复现的 18 例中,验证器结论与 md 根因**完全一致 5 例、部分一致 12 例、完全不重叠 1 例**;12 例部分一致的偏差点高度集中,全部可归因(见下)。

## 逐案例对比

判定口径:两侧 code 集合的交集。"一致"= 交不为空且验证器侧没有偏离 md 根因的噪声 code;"部分一致"= 交集非空但验证器多报或错报;"无原始记录"= md issueCodes 为空/缺失。

| case | 首跑 status | 验证器 codes | md 记录 codes | 结论 | 归因 |
|---|---|---|---|---|---|
| GEN-00116 | invalid | type-table-column-missing | type-table-column-missing | **一致** | |
| GEN-00120 | invalid | type-table-column-missing | type-table-column-missing | **一致** | |
| GEN-00030 | invalid | type-data-path-missing | type-data-path-missing | **一致** | |
| GEN-00056 | invalid | type-data-path-missing, unresolved-ref | unresolved-ref, type-data-path-missing | **一致** | |
| GEN-00247 | invalid | unknown-component | unknown-component | **一致** | |
| GEN-00042 | invalid | type-prop-mismatch, type-table-column-missing | type-table-column-missing | 部分一致 | 多报 type-prop-mismatch |
| GEN-00066 | invalid | type-data-path-missing, type-prop-mismatch, type-table-column-missing | type-data-path-missing | 部分一致 | 多报;含根因 |
| GEN-00124 | invalid | type-prop-mismatch, type-table-column-missing | type-table-column-missing | 部分一致 | 多报 type-prop-mismatch |
| GEN-00009 | invalid | unknown-component, unresolved-ref | unknown-component ×2, unresolved-ref | 部分一致 | 一致(actionable 去重);md 记重复 |
| GEN-00036 | invalid | type-prop-mismatch, unknown-component | unknown-component | 部分一致 | 多报 type-prop-mismatch |
| GEN-00122 | invalid | null-required, unknown-component | type-table-column-missing | 部分一致(偏差) | **builtin 错位**:契约有 String、注册表无 → @String 报 unknown-component,连带 null-required |
| GEN-00109 | invalid | unknown-component | type-table-column-missing | **不重叠** | **builtin 错位**:契约有 Flatten/MergeObject、注册表无 → @Flatten/@MergeObject 报 unknown-component,淹没 md 根因 |
| GEN-00118 | invalid | unknown-component | type-table-column-missing | 部分一致(偏差) | **builtin 错位**:@MergeObject 不在注册表 |
| GEN-00119 | invalid | null-required, type-data-path-missing, unknown-component | type-data-path-missing, type-table-column-missing ×3 | 部分一致(偏差) | builtin 错位(MergeObject)+ 多报;含 md 根因 type-data-path-missing |
| GEN-00100#2 | invalid | type-prop-mismatch, type-table-column-missing, unknown-component | (空) | **无原始记录** | md parser 静默崩溃,issueCodes=[] |
| GEN-00091#1 | invalid | excess-args, null-required, type-prop-mismatch, unknown-component | (空) | **无原始记录** | 同上 |
| GEN-00140 | invalid | unknown-component | (无) | **无原始记录** | 4b 小节没记 codes |
| GEN-00232/233/349 | not-reproduced | — | (无) | **无原始记录 + 缺 dataModel** | 回填 queryDeviceInfo 真值后预期 type-data-path-missing(已用近似数据验证) |
| GEN-00094/76/88 | not-reproduced | — | type-prop-mismatch | **不一致 → 缺 dataModel** | 回填 queryInterfaceMetrics 后预期 type-table-row-shape-mismatch(actionable 视图对 object[][] 的编码,已验证) |
| rw-333-group#1 | not-reproduced | — | builtin-argument-type-mismatch | **不一致 → 缺 dataModel** | 回填后 @ObjectEntries 对数组调用的类型错误应恢复 |
| en-device-distribution-fabric#3 | invalid | unknown-component | builtin-argument-type-mismatch | 部分一致(偏差) | builtin 错位(@MergeObject);回填侧 builtin-argument-type-mismatch 未出现,需 #42 落地后复跑确认 |

## 归因汇总(3 类,按占比)

1. **builtin 注册表错位(#42,另一条线在修)**: 影响 6 个复现案例(00109/00118/00122/00119/en-device/00100#2 的 unknown-component 面)。注册表缺 Flatten/ObjectValues/Number/String/Join/MergeObject,这些调用全被当 unknown-component 报出,既淹没真根因又产生噪声。#42 落地后此面自动消失。
2. **dataModel 缺失(T4 回填)**: 影响 7 个 not-reproduced 案例 + en-device 的 builtin-argument-type-mismatch 侧。已用近似数据验证 3 个代表案例回填后根因恢复(见上表注)。
3. **验证器多报(Stack 契约漂移,已接受的噪声)**: type-prop-mismatch / excess-args 出现在 4 个用 Stack 位置参数 `"m"`/`"l"`(gap)的 DSL 上——1.1.5 契约 Stack 无 gap prop。grilling 阶段已定按原文转化、接受此噪声,不改 case。

## 对 #42 的一个正面信号

@FormatNumber(1a 两例)在首跑中报出 **unknown-component**:1.1.5 契约没有 FormatNumber,契约驱动的 unknown-component 判定已经把它拦下来了。也就是说 1a 类幻觉的复现**不依赖** builtin 注册表修复,首跑即复现(md 里 issueCodes 为空是因为当年 parser 遇未知 @ 函数静默崩溃——现在不会了)。

## 下一步

- T4(缺口回填)后复跑 validation,预期:7 个 not-reproduced 全部转 invalid 且 codes 与 md 对齐;en-device 复核 builtin-argument-type-mismatch。
- #42 落地后复跑,预期:unknown-component 噪声面收敛到真幻觉(@GroupBy/@ObjectFromEntries 仍应报 unknown-component——契约里确实没有)。
- 本文件作为修复迭代基线:每轮改动后重跑对比。

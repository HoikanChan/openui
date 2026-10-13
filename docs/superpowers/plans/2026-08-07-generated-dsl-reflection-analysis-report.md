# Generated DSL 校验与反思修复分析报告 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution, or superpowers:subagent-driven-development only when the user explicitly requests delegated execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 `reask-fix-ab-report.html` 重做为一份中文、可审计、面向形式化验证专家的完整分析报告。

**Architecture:** 单文件静态 HTML，正文按证据强度、错误分类、反思数据流和真实运行轨迹组织。所有运行结论都从现有 metrics、LLM call logs、corpus dump 和当前测试结果提取；历史真实 prompt 与当前本地构造 prompt 分开标记。

**Tech Stack:** HTML5、CSS3、少量原生 JavaScript、PowerShell/Node 数据核对、Playwright 浏览器检查。

---

## File Structure

- Modify: `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html` — 完整报告正文、样式、目录和交互。
- Read only: `openspec/changes/add-generated-dsl-validation/benchmark-comparison.md` — Gate A/B 指标与实验限制。
- Read only: `openspec/changes/add-generated-dsl-validation/model-error-catalog.md` — 错误分类、频次和真实错误原文。
- Read only: `packages/genui-java-sdk/docs/generated-dsl-interception-report.md` — corpus 命中、known miss 和边界。
- Read only: `packages/genui-java-sdk/target/benchmark-out/{t07,t07-after}/**` — 116 次运行聚合与成功/失败案例。
- Read only: `packages/genui-java-sdk/target/interception-corpus-dump.md` — 当前 prompt 构造结果与级联抑制后的 issues。

### Task 1: 建立报告验收门槛与证据快照

**Files:**
- Modify: `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html:1`

- [ ] **Step 1: 运行旧报告结构审计，确认新结构尚不存在**

Run:

```powershell
$p='openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html'
$required=@('claim-matrix','error-taxonomy','reflection-pipeline','runtime-results','case-success','case-failure','known-misses','evidence-ledger')
$missing=$required|Where-Object{-not (Select-String -Quiet -Path $p -SimpleMatch "id=`"$_`"")}
if($missing.Count -eq 0){throw '旧报告意外包含全部新章节'}
"EXPECTED FAIL: missing " + ($missing -join ', ')
```

Expected: 输出至少一个缺失 section id。

- [ ] **Step 2: 从真实 metrics 重新计算前后结果**

Run:

```powershell
foreach($label in @('t07','t07-after')){
  $all=@()
  foreach($round in @('round1','round2')){
    $j=Get-Content -Raw -Encoding utf8 "packages/genui-java-sdk/target/benchmark-out/$label/$round/metrics-on.json"|ConvertFrom-Json
    $all+=@($j.fixtures)
  }
  $eligible=@($all|Where-Object status -ne 'SKIPPED')
  $valid=@($eligible|Where-Object status -eq 'VALID')
  $reasked=@($eligible|Where-Object {$_.reasks -gt 0})
  $repaired=@($reasked|Where-Object status -eq 'VALID')
  [pscustomobject]@{
    Run=$label; Eligible=$eligible.Count; Valid=$valid.Count
    ValidRate=[math]::Round(100*$valid.Count/$eligible.Count,1)
    Reasked=$reasked.Count; Repaired=$repaired.Count
    RepairRate=[math]::Round(100*$repaired.Count/$reasked.Count,1)
    MeanMs=[math]::Round(($eligible|Measure-Object durationMs -Average).Average)
    FirstDslMs=[math]::Round((@($eligible|Where-Object {$null -ne $_.firstDslMs})|Measure-Object firstDslMs -Average).Average)
  }
}
```

Expected:

```text
t07       116 102 87.9 16 2 12.5 5095 3084
t07-after 116 104 89.7 14 2 14.3 4702 2689
```

- [ ] **Step 3: 重建 HTML 语义骨架**

Replace the existing document with a valid single-file HTML shell containing these exact top-level sections:

```html
<main id="report">
  <section id="executive-summary"></section>
  <section id="claim-matrix"></section>
  <section id="error-taxonomy"></section>
  <section id="reflection-pipeline"></section>
  <section id="runtime-results"></section>
  <section id="case-success"></section>
  <section id="case-failure"></section>
  <section id="known-misses"></section>
  <section id="evidence-ledger"></section>
</main>
```

Include `<meta charset="utf-8">`, Chinese `<title>Generated DSL 校验与反思修复证据报告</title>`, and a no-dependency policy comment:

```html
<!-- Self-contained evidence report: no external fonts, scripts, images, or CDNs. -->
```

- [ ] **Step 4: 运行结构审计**

Run the Step 1 command with the final assertion changed to:

```powershell
if($missing.Count){throw "Missing sections: $($missing -join ', ')"}
'PASS: all required report sections exist'
```

Expected: `PASS: all required report sections exist`。

### Task 2: 写入中文错误分类、反思机制与真实运行案例

**Files:**
- Modify: `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html`

- [ ] **Step 1: 写执行摘要和属性矩阵**

Add the core finding exactly in Chinese:

```html
<h1>校验成立，修复尚未收敛</h1>
<p>当前系统能够在公开 DSL 流出前扣留已确定无效的完整语句；这一结论不蕴含反思必然收敛，也不蕴含数据路径、业务语义或视觉质量正确。</p>
```

The claim matrix must distinguish:

```text
无效语句被扣留       确定性属性   当前测试与 Gate A/B 支持
组件契约被检查       确定性属性   contract corpus 支持
引用最终闭合         条件属性     final validation 支持，repair 可能失败
反思能够修复         经验性结果   2/14 in t07-after
数据路径正确         已知反例     data.data.users / data.userz
视觉质量改善         未证明       overall 7.1 → 7.2，处于噪声范围
```

- [ ] **Step 2: 写入错误分类表**

Include at least these categories and exact representative fragments:

```text
JS 数组方法/箭头函数: baseline.map(item => item.value)
严格等于/成员判断: row.key === "avg_roam_time" / arr.includes(x)
JS 转换方法: data.totalDevices.toString()
JS 全局对象: Math.abs(data.delta)
编造/过时组件: cardHeader(...) / VLayout / Text / @Div
@Render arity/binder: @Render("v", expr, 2)
@Each 块语句: @Each(items,"d",{ id = d.id ... })
excess args: Card(..., fourthArg)
深层嵌套括号失衡
续写截断与 unresolved refs
```

For every category render columns for `原始错误`, `validator 观察`, `反思动作`, `终局/限制`。

- [ ] **Step 3: 写反思数据流和当前真实 prompt 语句**

Render this flow:

```text
LLM delta → statement boundary → validator → withhold invalid statement
→ assemble accepted prefix + invalid statement + issues + signatures
→ reask → same gate → VALID or error/done
```

Include verbatim current builder output under the label `当前本地构造 · 非历史 live 请求`:

```text
The same mistake pattern may recur later in the document. Apply the SAME correction to EVERY later occurrence, not just the first.
Your continuation MUST complete the document: every identifier referenced anywhere ... must be defined by the end.
`root` is NOT defined yet. Your continuation MUST end with a `root = Stack([...])` statement ...
Do NOT stop after the corrected statement. Keep writing until the document is complete.
Array `arr.map(x => x.f)` → pluck `arr.f`, or `@Each(arr, "x", x.f)`.
```

- [ ] **Step 4: 写真实成功案例 `actual-target-gap`**

Use the exact runtime facts:

```text
status=VALID, durationMs=5470, firstDslMs=2905, llmCalls=2, reasks=1, dslChars=490
issues: R_BRACE at 6:124; unclosed '{' at 6:27; unclosed '(' at 6:13
```

Show the invalid `gapCol`, the historical real reflection lines, the corrected `gapCol`, and `root = Stack([slaHeader, slaTable])`.

- [ ] **Step 5: 写真实失败案例 `tc-010-coverage-quality`**

Use the exact runtime facts:

```text
status=INVALID, durationMs=7324, firstDslMs=2169, llmCalls=2, reasks=1, dslChars=546
final unresolved refs: roamCard, coverageCard, throughputCard
```

Show that `.map(item => item.value)` was correctly rewritten to `@Each(..., "item", item.value)`, then explain that the model stopped before producing the rest of the document. Label this `局部修复成功 / 全局完整性失败`。

- [ ] **Step 6: 写 known miss 和证据限制**

Include these exact counterexamples:

```openui
root = Table([Col("Name", "name")], data.data.users)
root = Table([Col("Name", "nmae")], data.userz)
root = TextContent(data.value.toFixed(1))
```

State that all three currently return no validator issues, and separate the following limitations: 5 top-level-array fixtures skipped, independent OFF/ON sampling, judge ±1 noise, missing referenced eval run directories/screenshots, and current prompt lacking a fresh live A/B.

- [ ] **Step 7: Commit evidence content**

```powershell
git add -- openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html
git commit -m "docs: rebuild generated DSL reflection evidence report"
```

### Task 3: 实现“验证案卷”视觉与无依赖交互

**Files:**
- Modify: `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html`

- [ ] **Step 1: 实现视觉 token 和排版**

Define CSS variables with the approved palette:

```css
:root {
  --paper: #eee7da;
  --paper-raised: #f8f2e7;
  --ink: #28211b;
  --muted: #675b50;
  --rule: #b5a590;
  --red: #a42b1f;
  --green: #17614f;
  --amber: #8b5c14;
  --code: #27231f;
}
```

Use Georgia/serif for narrative content and Consolas/monospace for evidence, metrics, code, badges, and identifiers. Implement a fixed desktop table of contents, a single-column mobile layout under `900px`, and a maximum reading width around `1500px`.

- [ ] **Step 2: 实现证据组件**

Add reusable class shapes inside the single HTML file:

```text
.claim-card         conclusion + evidence level
.metric             numerator/denominator + scope
.error-card         taxonomy item
.trace              four-step failure/success trace
.source-badge       historical-live/current-local/static-corpus
.evidence-gap       missing or non-reproducible evidence
.code-block         scrollable DSL/prompt excerpt
```

- [ ] **Step 3: 实现渐进增强交互**

Add native JavaScript that:

```javascript
document.querySelectorAll('[data-toggle]').forEach((button) => {
  button.addEventListener('click', () => {
    const target = document.getElementById(button.dataset.toggle);
    const expanded = button.getAttribute('aria-expanded') === 'true';
    button.setAttribute('aria-expanded', String(!expanded));
    target.hidden = expanded;
  });
});
```

All expanded evidence must remain present in the HTML source. The default document must remain readable if JavaScript is disabled.

- [ ] **Step 4: 添加打印样式**

```css
@media print {
  nav, [data-toggle] { display: none !important; }
  body { background: white; color: black; }
  section, article { break-inside: avoid; }
  pre { white-space: pre-wrap; overflow: visible; }
}
```

- [ ] **Step 5: Commit visual implementation**

```powershell
git add -- openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html
git commit -m "docs: polish generated DSL evidence dossier"
```

### Task 4: 核对数据、浏览器渲染和最终工作区范围

**Files:**
- Verify: `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html`

- [ ] **Step 1: 运行内容审计**

```powershell
$p='openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html'
$required=@(
  '116','102','104','12.5%','14.3%','5095','4702','3084','2689',
  'actual-target-gap','tc-010-coverage-quality','roamCard','coverageCard','throughputCard',
  'data.data.users','data.userz','toFixed(1)','当前本地构造','历史真实运行'
)
$missing=$required|Where-Object{-not (Select-String -Quiet -Path $p -SimpleMatch $_)}
if($missing.Count){throw "Missing evidence: $($missing -join ', ')"}
'PASS: evidence strings present'
```

Expected: `PASS: evidence strings present`。

- [ ] **Step 2: 运行 HTML 静态安全检查**

```powershell
$p='openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html'
if(Select-String -Quiet -Path $p -Pattern 'https?://|<script\s+src=|<link\s+[^>]*href='){throw 'External dependency found'}
$placeholderPattern=('T'+'BD')+'|'+('T'+'ODO')+'|'+('待'+'补')+'|'+('待'+'定')
if(Select-String -Quiet -Path $p -Pattern $placeholderPattern){throw 'Placeholder found'}
git diff --check -- $p
'PASS: self-contained HTML and clean diff'
```

Expected: `PASS: self-contained HTML and clean diff`。

- [ ] **Step 3: 浏览器打开并截图**

Serve the worktree root:

```powershell
Start-Process -FilePath python -ArgumentList @('-m','http.server','8765','--bind','127.0.0.1') -WorkingDirectory (Get-Location) -WindowStyle Hidden
```

Open:

```text
http://localhost:8765/openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html
```

Verify at desktop and narrow width:

```text
desktop: 1440 × 1000
mobile:  390 × 844
```

Expected: no horizontal page overflow; code blocks scroll internally; sticky TOC appears only on desktop; evidence labels remain visible.

- [ ] **Step 4: 核对交互和打印**

Verify every `[data-toggle]` button changes `aria-expanded`, reveals the correct element, and does not hide content permanently. Open print preview and confirm navigation/buttons are absent while all evidence text remains printable.

- [ ] **Step 5: 确认没有越权改动**

```powershell
git status --short
git diff --name-only HEAD~2..HEAD
```

Expected: new implementation commits contain only `reask-fix-ab-report.html`; pre-existing worktree changes remain untouched.

- [ ] **Step 6: Final report**

Report the HTML path, key evidence sources, audit commands, browser checks, and any evidence that remains unavailable. Do not claim fresh live-model validation unless a new eval was actually run.

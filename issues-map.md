# GenUI Quality Issues Map

Derived from benchmark run `20260428_200424_uuxo` on `2026-04-28`.

- Fixtures: `49`
- Overall score: `7.71 / 10`
- Prior reference run: `20260428_102409_kd0s` at `7.74 / 10`

The headline score is effectively flat, but the distribution got slightly worse: perfect `10/10` fixtures dropped from `13` to `11`, and fixtures at `<= 6/10` rose from `12` to `13`.

---

## Snapshot

| Dimension | Current | Prior | Delta |
|---|---:|---:|---:|
| component_fit | 2.69 | 2.70 | -0.01 |
| data_completeness | 2.49 | 2.62 | -0.13 |
| format_quality | 2.49 | 2.53 | -0.04 |
| layout_coherence | 2.65 | 2.57 | +0.08 |
| overall | 7.71 | 7.74 | -0.03 |

Score distribution:

- `11` fixtures scored `10/10`
- `9` fixtures scored `9/10`
- `12` fixtures scored `8/10`
- `13` fixtures scored `<= 6/10`

Benchmark gate failures in this run:

- `items-with-number-arrays` (`7/10`)
- `items-with-tag-arrays` (`10/10`)
- `polymorphic-records` (`8/10`)
- `schema-inconsistent` (`10/10`)
- `tree-embedded-children` (`8/10`)

This means judge quality and parse/render correctness are not fully aligned. Two fixtures are judged `10/10` but still fail the benchmark gate, so quality work and runtime/test stability work should be tracked separately.

---

## Active Failure Clusters

### A. Primitive numeric arrays still have no good default rendering path `IMPACT: HIGH`

The current prompt still falls back to a raw table for bare number arrays, even when the task clearly asks for a distribution-style view.

Affected fixtures:

- `primitive-number-array` (`1/10`, `cf=0`, `dc=0`)
- `items-with-number-arrays` (`7/10`, benchmark gate failure)

Evidence:

- `primitive-number-array`: "uses a table for raw samples and omits the endpoint, sample count, and p95"
- `items-with-number-arrays`: latency arrays are not shown as per-row mini-distributions

Likely fix:

- Add a first-class histogram or mini-distribution rendering path for primitive number arrays
- Add prompt guidance that bare numeric samples should surface summary stats first, then a chart, not a table of raw values

---

### B. Dynamic-key and nested collection rendering is still fragile `IMPACT: HIGH`

The language is no longer blocked on dynamic-key data, but the generated DSL still fails to reliably materialize rows from maps and nested child collections.

Affected fixtures:

- `object-map-by-id` (`4/10`, `dc=1`, `fq=0`)
- `array-with-nested-arrays` (`4/10`, nested rows disappear)
- `timeseries-multi-entity-unaligned` (`5/10`, detail table renders no rows)
- `flat-parentid-reference` (`7/10`, hierarchy flattened)

What changed vs the previous map:

- `@ObjectEntries` and `@ObjectKeys` removed the original "cannot iterate object maps" blocker
- The remaining problem is adoption and rendering fidelity, not parser capability

Likely fix:

- Add stronger prompt examples for `value.*` access after `@ObjectEntries`
- Add one canonical nested-array example for "card per parent, table/list per child array"
- Add a regression fixture or prompt rule that forbids empty nested tables when the child array is non-empty

---

### C. Fabrication still happens when labels or values are missing `IMPACT: HIGH`

The model still invents labels or records instead of showing null/unknown states directly.

Affected fixtures:

- `unlabeled-ratio-array` (`4/10`)
- `nearly-all-null` (`5/10`)

Evidence:

- `unlabeled-ratio-array`: donut chart choice is fine, but labels are fabricated and values are not shown as percentages
- `nearly-all-null`: dashboard layout is clear, but it fabricates detection rows instead of showing unavailable metrics

Likely fix:

- Strengthen the anti-fabrication prompt rule
- Add an explicit unlabeled-ratio example: if labels do not exist, do not invent them
- Prefer `null`, `unknown`, or omission over synthetic category names or fake detail rows

---

### D. Specialized visual patterns are still underpowered `IMPACT: MEDIUM-HIGH`

Several fixtures want a more specific visualization than the current general-purpose chart/list fallback.

Affected fixtures:

- `timeseries-min-max-band` (`5/10`): still rendered as three lines instead of a range band
- `nodes-edges-graph` (`5/10`): nodes are shown, but edges are reduced to text
- `adjacency-list-graph` (`6/10`): dependency relationships are surfaced, but not as a real topology
- `record-with-sparkline` (`6/10`): oversized charts instead of compact inline sparklines

Likely fix:

- Complete the band/range chart path for min/max envelopes
- Decide whether graph/topology is a real product goal or an acceptable fallback class
- Tighten prompt rules for "compact inline trend" so sparkline-capable layouts are preferred inside record lists and tables

---

### E. Formatting is still inconsistent on positional and mixed-shape data `IMPACT: MEDIUM`

Formatting problems are no longer the dominant blocker, but they still suppress scores on otherwise-correct layouts.

Affected fixtures:

- `timeseries-tuple-pairs` (`6/10`, `fq=0`): timestamps stay raw
- `object-map-by-id` (`4/10`, `fq=0`): broken status/CPU/heartbeat display
- `cross-magnitude-values` (`6/10`): inconsistent byte units / broken top-card binding
- `byte-large-values` (`7/10`)
- `multi-top-arrays` (`8/10`): last online time rendered in the wrong form

Likely fix:

- Make formatting rules more shape-aware:
  - tuple timestamps must pass through `@FormatDate`
  - large byte values must use unit-aware formatting consistently
  - percent-like ratios should not appear as raw decimals

---

## Resolved Or Downgraded Since The Previous Map

### R1. Null-coalescing is no longer a primary parse blocker

Previous concern:

- `L1-C` tracked missing null-coalescing as a high-severity parse failure source

Current evidence:

- `schema-inconsistent` is now `10/10`
- `polymorphic-records` is now `8/10`

The remaining issue in `polymorphic-records` is presentation quality, not parser failure.

---

### R2. Tuple data is no longer blocked at component selection time

Previous concern:

- `L1-B` tracked tuple arrays as fundamentally unrenderable

Current evidence:

- `timeseries-tuple-pairs` now chooses the right chart type
- Remaining issue is timestamp formatting, not inability to project the tuple columns

---

### R3. Tree and hierarchy support improved, but fidelity is still incomplete

Previous concern:

- `L1-D` framed flat-to-tree conversion as a core blocker

Current evidence:

- `flat-parentid-reference` improved to `7/10`
- `tree-embedded-children` is judged `8/10` even though it still fails the benchmark gate

The remaining issue is that hierarchy metadata is incomplete or flattened, not that tree-like rendering is impossible.

---

### R4. Object-map builtins landed, but the user-facing quality win is not fully realized

Previous concern:

- `L1-A` tracked "no object iteration primitive"

Current evidence:

- The parser/runtime gap is addressed
- `object-map-by-id` is still only `4/10`, so prompt usage and rendering robustness are now the bottleneck

This issue should stay on the roadmap, but it has moved from "missing language feature" to "insufficient prompt/runtime follow-through".

---

## Priority Order

1. Fix primitive numeric array handling. This is the worst current failure and likely needs both prompt and component support.
2. Tighten anti-fabrication rules for null-heavy and unlabeled data.
3. Stabilize nested-array and object-map rendering so non-empty structures cannot silently render as empty.
4. Decide whether band charts, sparklines, and topology are first-class features or explicitly unsupported fallback cases.
5. Clean up formatting on tuple timestamps, large byte values, and ratio-like fields.
6. Investigate benchmark gate mismatch separately from judge quality scoring.

---

## Open Questions

- Should primitive number arrays map to a dedicated `HistogramChart`, a summary-plus-sparkline layout, or a more generic distribution component?
- For unlabeled ratio arrays, should the DSL prefer a chart without labels, synthetic ordinal labels (`Bucket 1`, `Bucket 2`, ...), or a non-chart representation?
- Is graph/topology rendering a real scope item, or should benchmark expectations be lowered to a structured fallback layout?
- Why do `items-with-tag-arrays` and `schema-inconsistent` still fail the benchmark gate while the judge scores them `10/10`?

---

*Generated: 2026-04-28 | Benchmark: 20260428_200424_uuxo | Suite: benchmark*

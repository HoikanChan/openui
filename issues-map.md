# GenUI Quality Issues Map

Derived from benchmark run `20260428_102409_kd0s` — 47 fixtures, overall **7.7/10**.

---

## Dimension Averages

| Dimension | Score | / Max |
|---|---|---|
| component_fit | 2.70 | 3 |
| data_completeness | 2.62 | 3 |
| format_quality | 2.53 | 3 |
| layout_coherence | 2.57 | 3 |
| **overall** | **7.74** | **10** |

Score distribution: 13× perfect 10, 7× score 9, 12× score 8 — but a long tail of 11 fixtures ≤ 6.

---

## Failure Taxonomy

### Layer 1 · Language / DSL Built-ins

#### L1-A · No map/object iteration  `IMPACT: HIGH`
Dynamic-key objects (`{ "dev-001": {...}, "dev-002": {...} }`) have no iteration primitive.
LLM hardcodes each key as a separate variable.

**Affected fixtures:** `object-map-by-id` (4/10), `grouped-object-of-arrays`, `date-keyed-buckets`, `timeseries-multi-entity-unaligned` (4/10)

**Fix:** Add `@ObjectEntries(obj) → [{key, value}, ...]` and `@ObjectKeys(obj) → string[]`

```
# Current (LLM's workaround):
card1 = Card([... data.dev-001.cpuUsage ...])
card2 = Card([... data.dev-002.cpuUsage ...])   ← hardcoded per key

# With @ObjectEntries:
rows = @ObjectEntries(data)
table = Table([nameCol, statusCol], rows)
nameCol = Col("Device", "key")
statusCol = Col("Status", "value.status", ...)
```

---

#### L1-B · No tuple column extraction  `IMPACT: MEDIUM`
Bare arrays of positional tuples `[[ts, val], [ts, val], ...]` can't project a column.
Named columnar data (`{ timestamps: [...], values: [...] }`) scores 10/10.
Tuple pairs score 5/10 because `@FormatDate(data, ...)` is applied to the whole array.

**Affected fixtures:** `timeseries-tuple-pairs` (5/10, fq:0)

**Fix:** `@Pluck(arr, index)` — extracts column `index` from each sub-array.

```
# With @Pluck:
timeLabels = @FormatDate(@Pluck(data, 0), "dateTime")
values     = @Pluck(data, 1)
series     = Series("Latency (ms)", values)
```

Or a more general data-transform `@Map(arr, "item", expr)` (distinct from template `@Each`).

---

#### L1-C · No null-coalescing operator  `IMPACT: HIGH (parse errors)`
String concatenation with nullable fields triggers `null-required` parse errors.
Two fixtures fail to parse entirely.

**Affected fixtures:** `schema-inconsistent` (parse error), `polymorphic-records` (parse error)

**Fix:** Add `??` null-coalescing operator to the expression grammar.

```
# Fails today:
row.cpuCores + "核 / " + row.ramGB + "GB 内存"   ← null-required if cpuCores is null

# With ?? operator:
(row.cpuCores ?? "—") + "核 / " + (row.ramGB ?? "—") + "GB 内存"
```

---

#### L1-D · No flat→tree conversion  `IMPACT: MEDIUM`
Flat arrays with `parentId` references require dynamic tree reconstruction.
LLM hardcodes the hierarchy by reading the sample data — resulting in wrong nesting.

**Affected fixtures:** `flat-parentid-reference` (5/10, lc:1)

**Fix:** `@BuildTree(arr, "id", "parentId") → nested {…, children: [...]} tree`

```
tree = @BuildTree(data, "id", "parentId")
# then pass to tree-rendering component
```

---

### Layer 2 · Components

#### L2-A · No Sparkline component  `IMPACT: HIGH`
`LineChart` inside a table cell renders at full chart dimensions — axis, legend, padding.
The LLM understands the sparkline intent but has no appropriate component.

**Affected fixtures:** `record-with-sparkline` (5/10, lc:1)

**Fix:** `Sparkline(values: number[], color?, width?, height?)` — compact inline chart, no axis/legend, designed for table cell contexts (20–60 px height).

Design question: new component vs `LineChart` with a `compact` mode?

---

#### L2-B · No distribution / histogram chart  `IMPACT: HIGH (worst score)**`
Bare number array `[12.4, 15.7, 11.2, ...]` has no appropriate visualization component.
LLM falls back to `Table` with index column — `component_fit: 0`, overall: 2/10.

**Affected fixtures:** `primitive-number-array` (2/10)

**Fix option A:** `HistogramChart(values: number[], binCount?: number)` — full component
**Fix option B:** `@Bucketize(values, binCount) → [{label, count}, ...]` built-in + existing `BarChart`

Option B is lower cost and more composable.

---

#### L2-C · No band/range series for AreaChart  `IMPACT: MEDIUM`
Min-max-value band time series requires expressing "area between two bounds."
LLM uses three separate `Series` in `LineChart` — judge scores `component_fit: 1`.

**Affected fixtures:** `timeseries-min-max-band` (5/10, cf:1)

**Fix:** `BandSeries(upper: number[], lower: number[], label?)` as a new Series subtype.
AreaChart renders this as a shaded band; the mean `Series` overlays as a line.

```
chart = AreaChart(timeLabels, [avgSeries, BandSeries(upperVals, lowerVals, "Range")])
```

---

#### L2-D · No graph/topology component  `IMPACT: LOW`
Node/edge graph data can only be rendered as text lists or manual grid layout.
Not viable to express connections visually.

**Affected fixtures:** `nodes-edges-graph` (5/10, cf:1)

**Fix:** `TopologyGraph(nodes, edges, labelField?, typeField?)` using a force-directed layout.
High implementation cost; low priority given that graph data is rare in dashboard contexts.

---

### Layer 3 · Prompt Engineering

#### P3-A · Fabrication on null/missing data  `IMPACT: HIGH (correctness)`
When most fields are null, the LLM invents plausible-looking data to fill its layout.
Existing rule "Never hardcode data values" is interpreted as "don't copy from data" — 
the LLM concludes it's fine to create its own values.

**Affected fixtures:** `nearly-all-null` (5/10)

**Fix:** Add explicit anti-fabrication rule:
> "If a field in the data model is null, absent, or undefined, display it as `null`, `—`, or omit it. NEVER generate, invent, or fabricate values that are not present in the data model."

---

#### P3-B · Nested array not surfaced  `IMPACT: MEDIUM`
When a record has an array field (e.g. `device.interfaces: [...]`), the LLM renders
the parent object but omits the nested array entirely.
No example or rule guides how to expand nested arrays inside a parent component.

**Affected fixtures:** `array-with-nested-arrays` (4/10, dc:1)

**Fix:** Add a prompt example showing the pattern:
```
deviceRows = Table([nameCol, interfacesCol], data.devices)
interfacesCol = Col("Interfaces", "interfaces", {
  cell: @Render("v", VLayout(@Each(v, "iface",
    Text(iface.ifName + " — " + iface.status, "small")), "xs"))
})
```

---

#### P3-C · Multi-entity top-level map not recognized  `IMPACT: MEDIUM`
The existing prompt example only covers multi-entity **interleaved rows**
(`Filter(data.rows, "portResId", "==", id)`).
When entities are already separated as top-level keys (`data.device_gz_core_01: [...]`),
the LLM doesn't know how to build per-entity series.

**Affected fixtures:** `timeseries-multi-entity-unaligned` (4/10, dc:1, fq:0)

**Fix:** Add a prompt example for the sub-array-per-entity pattern:
```
core01Series = Series("GZ-Core-01", data.device_gz_core_01.temperatureC)
core02Series = Series("GZ-Core-02", data.device_gz_core_02.temperatureC)
timeLabels   = @FormatDate(data.device_gz_core_01.timestamp, "dateTime")
chart = LineChart(timeLabels, [core01Series, core02Series], "smooth")
```

---

## Priority Matrix

```
                      IMPACT (fixtures × score delta)
                      Low                    High
                ┌────────────────────────────────────┐
          Low   │  TopologyGraph     @BuildTree       │
  Impl          │  BandSeries                         │
  Cost   ───────┼────────────────────────────────────┤
          High  │  HistogramChart    ?? operator  ←fast win
                │  Sparkline         @ObjectEntries   │
                │                    @Pluck           │
                │                    anti-fabrication │
                │                    prompt examples  │
                └────────────────────────────────────┘
```

### Quick wins (low cost, high impact)
1. `??` null-coalescing — 1 parser change, fixes 2 parse errors immediately
2. Anti-fabrication prompt rule — text change only
3. `@ObjectEntries` built-in — unlocks entire map/dict fixture class
4. `@Pluck` built-in — unlocks tuple array fixture class
5. Nested array + multi-entity prompt examples

### Medium term
6. `Sparkline` component
7. `@BuildTree` built-in
8. `@Bucketize` + HistogramChart

### Long term
9. `BandSeries` for AreaChart
10. `TopologyGraph` component

---

## Structural Asymmetry: Named vs Positional Data

The benchmark exposes a fundamental DSL bias:

```
Named columnar (10/10):       Positional tuple (5/10):
{ timestamps: [...],          [[ts, val],
  values: [...] }              [ts, val], ...]

data.timestamps  ← works      data[n][0]  ← no built-in to project
```

Named fields are first-class citizens; positional array indices are not.
`@Pluck` closes this gap. The deeper question: should data reshaping happen
in the DSL or upstream before the data reaches the component?

---

## Open Questions

- Should `@ObjectEntries` be `@ToRows(obj, "key", "value")` for clearer semantics?
- `??` only, or `?.` optional chaining too? (`row.nested?.field` is common)
- `Sparkline` as a new component or `LineChart` with `compact` mode?
- Is `@BuildTree` too opinionated? (assumes `id`/`parentId` convention)
- Should the LLM receive a "data shape classification" step before DSL generation?
  (Classify → route to appropriate component pattern → then generate)

---

*Generated: 2026-04-28 | Benchmark: 20260428_102409_kd0s | Suite: benchmark*

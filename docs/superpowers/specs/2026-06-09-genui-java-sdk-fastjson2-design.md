# GenUI Java SDK Fastjson2 Migration Design

## Goal

Replace the custom JSON parser and serializer in `packages/genui-java-sdk` with
Alibaba Fastjson2 while preserving the SDK's current observable behavior.

The migration is accepted only when every existing prompt golden file remains
byte-for-byte unchanged and the full Maven test suite passes.

## Scope

- Add `com.alibaba.fastjson2:fastjson2:2.0.61` as a Maven runtime dependency.
- Keep the package-private `Json` class as a small SDK adapter.
- Remove the custom parser, compact serializer, string escaping, and scalar
  number formatting implementations from `Json.java`.
- Remove the Maven-free `run-tests.sh` runner.
- Update the Java SDK README to document Maven test commands.
- Do not change prompt assembly rules, golden fixtures, public SDK APIs, or
  unrelated packages.

## Adapter Behavior

`Json` remains the single JSON policy boundary for the SDK:

- `parse(String)` delegates parsing to Fastjson2 and returns JSON object and
  array structures with stable source order.
- Parsed objects and arrays are recursively normalized to `LinkedHashMap` and
  `ArrayList`.
- Parsed integral numbers are normalized to `Long`; decimal and exponent
  numbers are normalized to `Double`, matching the current parser.
- `stringify(Object)` delegates compact serialization to Fastjson2.
- `stringifyPretty(Object)` uses a small structure-only formatter because
  Fastjson2's two-space feature omits the space after `:`. Maps and lists are
  indented by the adapter, while every scalar value and object key is serialized
  by Fastjson2. This preserves JavaScript `JSON.stringify(value, null, 2)`
  whitespace without retaining custom escaping or number serialization.
- Non-finite `Float` and `Double` values serialize as JSON `null`.
- `asObject` and `asList` remain SDK-owned validation helpers and retain their
  existing `GenerationSdkException` messages.
- Fastjson2 parse failures are translated at the adapter boundary so callers do
  not depend on Fastjson2 exception types.

No production caller should invoke Fastjson2 directly.

## Golden Compatibility

The authoritative compatibility corpus is:

`packages/genui-java-sdk/src/test/resources/prompt-golden`

The eight existing `.json` inputs and matching `.txt` outputs must not be
modified by this change. `PromptGoldenTest` must continue comparing generated
prompts against those files exactly, including whitespace and line endings.

## Testing

Extend `JsonTest` to cover:

- object and array parsing with stable key order;
- integral values parsed as `Long` and decimal/exponent values parsed as
  `Double`;
- compact serialization;
- two-space pretty serialization, including `": "` spacing;
- non-finite floating-point values serialized as `null`;
- invalid JSON translated to the SDK exception boundary.

Run:

```bash
mvn -Dtest=JsonTest test
mvn -Dtest=PromptGoldenTest test
mvn test
```

Success requires all existing 22 tests plus the new adapter tests to pass. The
prompt-golden fixture files must have no Git diff.

## Documentation And Build

Maven is the only supported build and test path for this module. The README will
replace `run-tests.sh` examples with:

```bash
mvn test
mvn -Dtest=PromptGoldenTest test
```

The implementation will delete `run-tests.sh` rather than maintaining a second
dependency-resolution path.

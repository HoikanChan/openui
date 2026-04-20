## 1. Flatten component schemas

- [x] 1.1 Inventory the affected public components in `packages/react-ui-dsl/src/genui-lib` and identify every schema that still exposes `properties`, `style`, or top-level `actions`
- [x] 1.2 Update each affected `schema.ts` so semantic fields move from `properties.*` to top-level props and removed `style` / `actions` fields no longer appear in the exported DSL contract
- [x] 1.3 Review already-flattened components such as `Table`, `Col`, and `Card` and remove any remaining exported `style` or top-level `actions` fields that should no longer be part of the LLM-facing schema

## 2. Update render adapters and prompt-facing metadata

- [x] 2.1 Refactor each affected component entry point and any view adapter to consume top-level props directly instead of `props.properties.*`
- [x] 2.2 Update component descriptions, stories, and fixture inputs so all prompt-facing examples and authored DSL use the flattened schema
- [x] 2.3 Regenerate or update any e2e DSL snapshots and related assertions that currently depend on nested `properties`, `style`, or removed `actions` fields

## 3. Add regression coverage and verify the package

- [x] 3.1 Add or update tests that assert `dslLibrary.prompt()` and `dslLibrary.toJSONSchema()` no longer expose `properties`, `style`, or top-level `actions` for affected components
- [x] 3.2 Run the relevant `packages/react-ui-dsl` validation commands (`test`, `test:e2e`, and `typecheck` or targeted equivalents) and fix any breakages caused by the schema migration
- [x] 3.3 Review the final prompt/schema output and changed examples to confirm the preferred authoring path is the flattened top-level DSL with no compatibility fallback

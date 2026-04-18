## 1. Schema redesign

- [ ] 1.1 Design the new openui lang-style `Table` and `Col` component signatures for `packages/react-ui-dsl/src/genui-lib/Table`
- [ ] 1.2 Replace the legacy JSON-style column object schema with the new component-oriented schema definition
- [ ] 1.3 Map sorting, filtering, formatting, tooltip, and custom cell capabilities onto the new column schema

## 2. Runtime translation

- [ ] 2.1 Refactor the table runtime to translate the new `Table` schema into antd `columns` and related render config
- [ ] 2.2 Preserve the existing antd table rendering path while switching the DSL input shape
- [ ] 2.3 Add either a compatibility adapter or a migration error path for legacy `properties.columns[*]` inputs

## 3. Prompt and verification

- [ ] 3.1 Update `dslLibrary` table descriptions, examples, and prompt guidance so the preferred output is the new openui lang-style schema
- [ ] 3.2 Add or update tests covering schema parsing, schema-to-antd translation, and legacy migration behavior
- [ ] 3.3 Verify at least one generated table example can be expressed without the old full JSON-style structure

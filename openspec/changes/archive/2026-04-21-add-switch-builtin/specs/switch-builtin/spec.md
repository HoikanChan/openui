## ADDED Requirements

### Requirement: Switch builtin maps enum values to display results
The system SHALL provide a `@Switch(value, cases, default?)` builtin that looks up `value` in the `cases` object and returns the matching result, or `default` (null if omitted) when no case matches. The `value` argument SHALL be coerced to a string for key lookup so that numeric enum values match string object keys.

#### Scenario: Integer enum matches correct case
- **WHEN** `@Switch(1, {0: "Pending", 1: "Active", 2: "Inactive"})` is evaluated
- **THEN** the result is `"Active"`

#### Scenario: String enum matches correct case
- **WHEN** `@Switch("admin", {admin: "管理员", user: "用户"})` is evaluated
- **THEN** the result is `"管理员"`

#### Scenario: No match returns explicit default
- **WHEN** `@Switch(99, {0: "A", 1: "B"}, "Unknown")` is evaluated
- **THEN** the result is `"Unknown"`

#### Scenario: No match with no default returns null
- **WHEN** `@Switch(99, {0: "A"})` is evaluated
- **THEN** the result is `null`

#### Scenario: Null value coerces to empty string key
- **WHEN** `@Switch(null, {"": "empty", 0: "zero"}, "fallback")` is evaluated
- **THEN** the result is `"empty"`

#### Scenario: Null value with no matching key returns default
- **WHEN** `@Switch(null, {0: "zero"}, "fallback")` is evaluated
- **THEN** the result is `"fallback"`

#### Scenario: Non-object cases returns default
- **WHEN** `@Switch(1, null, "fallback")` is evaluated
- **THEN** the result is `"fallback"`

#### Scenario: Switch composes inside Each
- **WHEN** `@Each(rows, "item", @Switch(item.status, {0: "Pending", 1: "Active"}))` is evaluated with `rows = [{status: 0}, {status: 1}]`
- **THEN** the result is `["Pending", "Active"]`

#### Scenario: Switch result is available as component prop
- **WHEN** a component prop is set to `@Switch(item.type, {admin: "red", user: "blue"}, "gray")`
- **THEN** the prop value resolves to the matched color string at render time

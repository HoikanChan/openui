## ADDED Requirements

### Requirement: DSL Card SHALL expose variant and width as its only visual props
The DSL `Card` component schema SHALL define exactly two visual props: `variant` (the visual style) and `width` (the container width). It SHALL NOT expose flex layout props (`direction`, `gap`, `align`, `justify`, `wrap`).

#### Scenario: Card schema contains variant and width
- **WHEN** a consumer inspects the exported schema or prompt signature for `Card`
- **THEN** `variant` and `width` appear as accepted props
- **AND** `direction`, `gap`, `align`, `justify`, and `wrap` do not appear

#### Scenario: Card with flex props fails validation
- **WHEN** a DSL definition passes `gap`, `direction`, `align`, `justify`, or `wrap` to `Card`
- **THEN** schema validation rejects the definition

### Requirement: DSL Card width prop SHALL match React UI Card values
The DSL `Card` `width` prop SHALL accept `"standard"` and `"full"`, matching the React UI `Card` component's `width` prop exactly. The default SHALL be `"standard"`.

#### Scenario: Card renders at standard width by default
- **WHEN** a Card is defined without a `width` prop
- **THEN** the rendered card uses the `"standard"` width variant

#### Scenario: Card accepts full width
- **WHEN** a Card is defined with `width="full"`
- **THEN** the rendered card uses the `"full"` width variant

### Requirement: DSL Card children SHALL accept any DSL component
The DSL `Card` `children` prop SHALL accept any DSL component node. The component description SHALL explicitly state that any library component may be placed inside a Card, and SHALL recommend using VLayout or HLayout for internal layout needs.

#### Scenario: Card accepts arbitrary DSL component children
- **WHEN** a Card contains children of any DSL component type (e.g., Table, Text, CardHeader, VLayout)
- **THEN** the Card renders those children without validation errors

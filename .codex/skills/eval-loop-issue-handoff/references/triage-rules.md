# Triage Rules

Use these rules to decide what becomes a Linear capability fix issue.

## Raise The Issue If

- the problem affects multiple fixtures
- one fixture is extremely bad, usually `<= 4/10`
- the mechanism is parser/runtime related:
  - `null-required`
  - `unknown-component`
  - render failure
- benchmark status and judge score contradict each other
- the likely fix improves overall score distribution or eval trustworthiness
- the evidence points to a reusable data-shape or rendering capability gap

## Usually Do Not Raise If

- it is purely cosmetic
- it affects only one fixture and has no broader mechanism
- it is a specialized visual capability with low overall impact, unless explicitly requested
- the issue title would have to be vague, such as:
  - "improve prompt"
  - "fix benchmark"
  - "make charts better"
- the only plausible fix is hardcoding a fixture-specific exception

## Good Grouping Patterns

- primitive numeric arrays default to raw tables
- object maps and nested arrays drop rows
- null-heavy or unlabeled data causes fabrication
- tuple timestamps / bytes / ratios are formatted incorrectly
- benchmark failures and judge scores disagree
- compact trend fields are rendered as oversized charts
- chart-ready fields are ignored or raw rows are fabricated into chart props

## Bad Grouping Patterns

- one issue per fixture when the mechanism is the same
- one giant issue covering every low-scoring fixture
- grouping by component family when the root causes differ
- using judge dimensions such as "Poor value formatting" as the final title without naming the capability

## Reporting Principle

Prefer a narrow issue with strong evidence over a broad issue with weak evidence.

Name the capability in the title. List fixtures only inside the evidence section.

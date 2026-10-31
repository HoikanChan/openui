#!/usr/bin/env python3
"""Run the lint regression fixtures and compare the reports with expected.json.

The fixture project deliberately contains violations for the six Checkstyle rules that the
production ruleset relies on and for the three PMD semantic rules added for G.FMT.20, G.OBJ.04,
and G.CTL.05. Both the positive and the negative fixtures are asserted, so a check that silently
stops firing and a check that starts over-reporting both fail this script.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

CHECKSTYLE_RULES = {
    "MissingJavadocMethod",
    "JavadocMethod",
    "SummaryJavadoc",
    "WriteTag",
    "RegexpHeader",
    "BooleanExpressionComplexity",
}
PMD_RULES = {
    "MissingNumericSuffix",
    "LocalVariableShadowsField",
    "AvoidReassigningLoopVariables",
}


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def run_maven(args: list[str], cwd: Path) -> None:
    command = ["mvn", "-B", *args]
    if os.name == "nt":
        command = ["cmd", "/c", *command]
    print("+", " ".join(command))
    subprocess.run(command, cwd=cwd, check=True)


def normalize(path: str, fixtures_root: Path) -> str:
    relative = os.path.relpath(Path(path).resolve(), fixtures_root.resolve())
    return relative.replace(os.sep, "/")


def checkstyle_rule(error: ET.Element) -> str:
    source = error.get("source", "")
    simple_name = source.rsplit(".", 1)[-1]
    return re.sub(r"Check$", "", simple_name)


def read_checkstyle(report: Path, fixtures_root: Path) -> list[tuple[str, str, int]]:
    found = []
    root = ET.parse(report).getroot()
    for file_element in root:
        file_name = normalize(file_element.get("name", ""), fixtures_root)
        for error in file_element:
            rule = checkstyle_rule(error)
            if rule in CHECKSTYLE_RULES:
                found.append((file_name, rule, int(error.get("line"))))
    return found


def read_pmd(report: Path, fixtures_root: Path) -> list[tuple[str, str, int]]:
    found = []
    root = ET.parse(report).getroot()
    for file_element in root:
        file_name = normalize(file_element.get("name", ""), fixtures_root)
        for violation in file_element:
            rule = violation.get("rule", "")
            if rule in PMD_RULES:
                found.append((file_name, rule, int(violation.get("beginline"))))
    return found


def flatten_expected(section: dict[str, list[list[object]]]) -> list[tuple[str, str, int]]:
    expected = []
    for file_name, violations in section.items():
        for rule, line in violations:
            expected.append((file_name, rule, int(line)))
    return expected


def assert_negative(
    actual: list[tuple[str, str, int]], negatives: dict[str, list[str]]
) -> list[str]:
    problems = []
    for file_name, rules in negatives.items():
        for rule in rules:
            hits = [entry for entry in actual if entry[0] == file_name and entry[1] == rule]
            if hits:
                problems.append(
                    f"negative fixture {file_name} unexpectedly reports {rule}: {hits}"
                )
    return problems


def main() -> int:
    root = repo_root()
    fixtures_project = root / "tools" / "lint-regression"
    fixtures_root = fixtures_project / "fixtures" / "src" / "main" / "java"
    expected = json.loads((fixtures_project / "expected.json").read_text(encoding="utf-8"))

    # The fixture project is standalone, so the custom rule jar must be in the local repository.
    run_maven(["-N", "install", "-DskipTests"], root)
    run_maven(["-f", str(root / "tools" / "lint-rules" / "pom.xml"), "install", "-DskipTests"], root)
    run_maven(["checkstyle:checkstyle", "pmd:pmd"], fixtures_project)

    actual_checkstyle = read_checkstyle(
        fixtures_project / "target" / "checkstyle-result.xml", fixtures_root
    )
    actual_pmd = read_pmd(fixtures_project / "target" / "pmd.xml", fixtures_root)

    problems = []
    for name, actual, section in (
        ("checkstyle", actual_checkstyle, "checkstyle"),
        ("pmd", actual_pmd, "pmd"),
    ):
        expected_entries = flatten_expected(expected[section])
        if sorted(actual) != sorted(expected_entries):
            missing = sorted(set(expected_entries) - set(actual))
            unexpected = sorted(set(actual) - set(expected_entries))
            problems.append(f"{name} mismatch")
            if missing:
                problems.append(f"  missing: {missing}")
            if unexpected:
                problems.append(f"  unexpected: {unexpected}")
        print(f"{name}: {len(actual)} tracked violation(s)")

    problems.extend(assert_negative(actual_checkstyle, expected["negativeCheckstyle"]))
    problems.extend(assert_negative(actual_pmd, expected["negativePmd"]))

    positive_checkstyle = {entry[1] for entry in flatten_expected(expected["checkstyle"])}
    positive_pmd = {entry[1] for entry in flatten_expected(expected["pmd"])}
    for rule in sorted(CHECKSTYLE_RULES - positive_checkstyle):
        problems.append(f"no positive fixture for Checkstyle rule {rule}")
    for rule in sorted(PMD_RULES - positive_pmd):
        problems.append(f"no positive fixture for PMD rule {rule}")

    if problems:
        print("\nREGRESSION FAILED", file=sys.stderr)
        for problem in problems:
            print(problem, file=sys.stderr)
        return 1
    print("Lint regression passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env node

import assert from "node:assert/strict";
import { parseSpec, assertMeasurements } from "./check-spec.mjs";

const scenario = parseSpec({ source: "self-test", scenarios: [{ id: "text", dsl: "root = TextContent(\"42\")", expectText: ["42"] }] }).scenarios[0];
const box = { x: 0, y: 0, width: 100, height: 20, right: 100, bottom: 20 };
const measurement = { found: true, text: "42", box, elements: [], children: [], parents: {}, viewportWidth: 1200, documentScrollWidth: 1200 };
const passing = [];
assertMeasurements(scenario, measurement, passing);
assert.deepEqual(passing, []);
const missingText = [];
assertMeasurements({ ...scenario, expectText: ["99"] }, measurement, missingText);
assert.match(missingText[0], /missing text/);
const wrapped = {
  ...measurement,
  children: [
    { path: "0/0", tag: "DIV", text: "", box },
    { path: "0/1", tag: "DIV", text: "", box: { ...box, y: 40 } },
  ],
  parents: { "0": [{ path: "0", text: "", box }] },
};
const wrapping = [];
assertMeasurements({ ...scenario, sameRow: [{ path: "0" }] }, wrapped, wrapping);
assert.match(wrapping[0], /unexpected wrapping/);
const empty = [];
assertMeasurements({ ...scenario, sameRow: [{ path: "0" }] }, measurement, empty);
assert.match(empty[0], /matched 0 nodes/);
const unused = {
  ...wrapped,
  children: [
    { path: "0/0", tag: "DIV", text: "", box },
    { path: "0/1", tag: "DIV", text: "", box: { ...box, right: 80 } },
  ],
  parents: { "0": [{ path: "0", text: "", box: { ...box, width: 200, right: 200 } }] },
};
const whitespace = [];
assertMeasurements({ ...scenario, childrenFillWidth: [{ path: "0" }] }, unused, whitespace);
assert.match(whitespace[0], /children leave 120px/);
console.log("5 self-tests passed: text acceptance, missing text, wrapping, missing target, and unused width.");

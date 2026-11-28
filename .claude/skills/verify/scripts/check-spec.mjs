import { z } from "../../../../packages/react-ui-dsl/node_modules/zod/index.js";

const Box = z.object({ x: z.number(), y: z.number(), width: z.number(), height: z.number(), right: z.number(), bottom: z.number() });
const ElementMeasurement = Box.extend({
  scrollWidth: z.number(), scrollHeight: z.number(), clientWidth: z.number(), clientHeight: z.number(),
  overflowX: z.string(), overflowY: z.string(), whiteSpace: z.string(),
});
const Target = z.object({ path: z.string().regex(/^\d+(?:\/\d+)*$/), text: z.string().optional() });

export const MeasurementSchema = z.object({
  found: z.boolean(), text: z.string(), box: Box.nullable(),
  elements: z.array(ElementMeasurement),
  children: z.array(z.object({ path: z.string(), tag: z.string(), text: z.string(), box: Box })),
  parents: z.record(z.string(), z.array(z.object({ path: z.string(), text: z.string(), box: Box }))),
  viewportWidth: z.number(), documentScrollWidth: z.number(),
});

export const parseSpec = (input) => z.object({
  source: z.string().min(1),
  scenarios: z.array(z.object({
    id: z.string().regex(/^[a-z0-9][a-z0-9-]*$/),
    dsl: z.string().min(1),
    data: z.record(z.string(), z.unknown()).default({}),
    width: z.number().int().positive().default(1200),
    viewport: z.object({ width: z.number().int().positive(), height: z.number().int().positive() }).optional(),
    expectText: z.array(z.string()).default([]),
    absentText: z.array(z.string()).default([]),
    allowParserErrors: z.boolean().default(false),
    allowRuntimeErrors: z.boolean().default(false),
    allowedConsoleErrors: z.array(z.string()).default([]),
    noHorizontalOverflow: z.boolean().default(true),
    noClipping: z.boolean().default(false),
    sameRow: z.array(Target).default([]),
    childrenFillWidth: z.array(Target).default([]),
    tolerance: z.number().nonnegative().default(2),
    readyDelayMs: z.number().int().nonnegative().default(500),
  })).min(1),
}).parse(input);

function matchingChildren(measurement, target, failures, scenario) {
  const matches = measurement.parents[target.path] ?? [];
  if (matches.length !== 1) {
    failures.push(`${scenario.id}: target ${target.path}${target.text ? ` containing ${JSON.stringify(target.text)}` : ""} matched ${matches.length} nodes, expected one`);
    return null;
  }
  const children = measurement.children.filter((child) => child.path.startsWith(`${target.path}/`) && child.path.split("/").length === target.path.split("/").length + 1);
  if (children.length < 2) {
    failures.push(`${scenario.id}: target ${target.path} has ${children.length} children, expected at least two`);
    return null;
  }
  return { parent: matches[0], children };
}

export const assertMeasurements = (scenario, measurement, failures) => {
  if (!measurement.found) {
    failures.push(`${scenario.id}: no rendered content`);
    return;
  }
  for (const text of scenario.expectText) {
    if (!measurement.text.includes(text)) failures.push(`${scenario.id}: missing text ${JSON.stringify(text)}`);
  }
  for (const text of scenario.absentText) {
    if (measurement.text.includes(text)) failures.push(`${scenario.id}: unexpected text ${JSON.stringify(text)}`);
  }
  if (scenario.noHorizontalOverflow && measurement.documentScrollWidth > measurement.viewportWidth + 1) {
    failures.push(`${scenario.id}: document scroll width ${measurement.documentScrollWidth} exceeds viewport ${measurement.viewportWidth}`);
  }
  if (scenario.noClipping) {
    for (const element of measurement.elements) {
      if (element.width <= 0 || element.height <= 0) continue;
      const horizontal = element.scrollWidth > element.clientWidth + 1 && element.overflowX !== "auto" && element.overflowX !== "scroll";
      const vertical = element.scrollHeight > element.clientHeight + 1 && element.overflowY !== "auto" && element.overflowY !== "scroll";
      if (horizontal || vertical) {
        failures.push(`${scenario.id}: clipped content ${JSON.stringify({ clientWidth: element.clientWidth, scrollWidth: element.scrollWidth, clientHeight: element.clientHeight, scrollHeight: element.scrollHeight })}`);
      }
    }
  }
  for (const target of scenario.sameRow) {
    const match = matchingChildren(measurement, target, failures, scenario);
    if (!match) continue;
    const [first, ...rest] = match.children;
    for (const child of rest) {
      if (Math.abs(child.box.y - first.box.y) > scenario.tolerance) failures.push(`${scenario.id}: unexpected wrapping in ${target.path}`);
    }
  }
  for (const target of scenario.childrenFillWidth) {
    const match = matchingChildren(measurement, target, failures, scenario);
    if (!match) continue;
    const last = match.children[match.children.length - 1];
    const unused = match.parent.box.right - last.box.right;
    if (Math.abs(unused) > scenario.tolerance) failures.push(`${scenario.id}: children leave ${unused}px before the edge of ${target.path}`);
  }
};

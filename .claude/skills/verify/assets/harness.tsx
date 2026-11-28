import React from "react";
import { createRoot } from "react-dom/client";
import { Renderer, createParser, dslLibrary } from "@openuidev/react-ui-dsl";

type LayoutTarget = { path: string; text?: string };

declare global {
  interface Window {
    openUIVerification: {
      errors: Record<string, unknown[]>;
      collectLayout: (scenario: Scenario) => {
        parents: Record<string, { path: string; text: string; box: Box }[]>;
        children: { path: string; tag: string; text: string; box: Box }[];
      } | null;
    };
  }
}

type Box = { x: number; y: number; width: number; height: number; right: number; bottom: number };
type Scenario = {
  id: string;
  dsl: string;
  data: Record<string, unknown>;
  width: number;
  sameRow: LayoutTarget[];
  childrenFillWidth: LayoutTarget[];
};

const scenarios: Scenario[] = await fetch("/openui-verification-cases.json").then((response) => response.json());
const parser = createParser(dslLibrary.toJSONSchema(), undefined, { externalRefs: ["data"] });
const errors: Record<string, unknown[]> = {};
const flattenErrors = (value: unknown): unknown[] => Array.isArray(value) ? value.flatMap(flattenErrors) : [value];

function readBox(element: Element): Box {
  const rect = element.getBoundingClientRect();
  return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom };
}

window.openUIVerification = {
  errors,
  collectLayout(scenario) {
    const root = document.querySelector(`[data-case="${scenario.id}"] [data-render]`);
    if (!root) return null;
    const targets = [...scenario.sameRow, ...scenario.childrenFillWidth].filter((target, index, all) => all.findIndex((candidate) => candidate.path === target.path && candidate.text === target.text) === index);
    const parents = Object.fromEntries(targets.map((target) => [target.path, [] as { path: string; text: string; box: Box }[]]));
    const children: { path: string; tag: string; text: string; box: Box }[] = [];
    const walk = (element: Element, path: string) => {
      for (const target of targets) {
        if (target.path === path && (!target.text || (element.textContent ?? "").includes(target.text))) {
          parents[path].push({ path, text: element.textContent ?? "", box: readBox(element) });
        }
      }
      [...element.children].forEach((child, index) => {
        const childPath = path ? `${path}/${index}` : String(index);
        children.push({ path: childPath, tag: child.tagName, text: child.textContent ?? "", box: readBox(child) });
        walk(child, childPath);
      });
    };
    walk(root, "");
    return { parents, children };
  },
};

function CaseView({ scenario }: { scenario: Scenario }) {
  const [parseErrors, setParseErrors] = React.useState<unknown[]>([]);
  React.useEffect(() => {
    setParseErrors(parser.parse(scenario.dsl).meta.errors);
  }, [scenario.dsl]);
  return (
    <section data-case={scenario.id} style={{ width: scenario.width, margin: 24, outline: "1px solid #bbb" }}>
      <h1>{scenario.id}</h1>
      <pre data-errors>{JSON.stringify(parseErrors)}</pre>
      <div data-render>
        <Renderer
          library={dslLibrary}
          response={scenario.dsl}
          dataModel={scenario.data}
          onError={(error) => {
            (errors[scenario.id] ??= []).push(...flattenErrors(error));
          }}
        />
      </div>
    </section>
  );
}

createRoot(document.getElementById("root")!).render(
  <main>{scenarios.map((scenario) => <CaseView key={scenario.id} scenario={scenario} />)}</main>,
);

import fs from "node:fs";
import path from "node:path";
import { describe, expect, test } from "vitest";

const componentDirs = [
  "Button",
  "Card",
  "Form",
  "Image",
  "Input",
  "Link",
  "Descriptions",
  "Select",
  "Stack",
  "Tag",
  "Table",
  "Tabs",
  "Charts/BarChart",
  "Charts/LineChart",
  "Charts/PieChart",
];

function resolveComponentPath(componentDir: string): string {
  return path.resolve(__dirname, componentDir);
}

describe("genui-lib story structure", () => {
  test.each(componentDirs)("%s exposes view and story files", (componentDir) => {
    const componentPath = resolveComponentPath(componentDir);

    expect(fs.existsSync(path.join(componentPath, "view", "index.tsx"))).toBe(true);
    expect(fs.existsSync(path.join(componentPath, "stories", "index.stories.tsx"))).toBe(true);
  });
});

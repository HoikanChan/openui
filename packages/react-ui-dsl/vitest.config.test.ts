import { describe, expect, it } from "vitest";

import { getTestExcludePatterns } from "./vitest.config";

describe("vitest config", () => {
  it("keeps fuzz tests out of the default e2e run", () => {
    expect(getTestExcludePatterns({})).toContain("src/__tests__/e2e/dsl-fuzz.test.tsx");
  });

  it("allows running the fuzz suite explicitly", () => {
    expect(getTestExcludePatterns({ REACT_UI_DSL_E2E_SUITE: "fuzz" })).not.toContain(
      "src/__tests__/e2e/dsl-fuzz.test.tsx",
    );
  });
});

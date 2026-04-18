import { describe, expect, it } from "vitest";
import { generatePrompt, type PromptSpec } from "../prompt";

describe("generatePrompt dataModel support", () => {
  const baseSpec: PromptSpec = {
    root: "Root",
    components: {
      Root: { signature: "Root(children: Component[])", description: "Root container" },
      Label: { signature: "Label(text: string)", description: "Simple text label" },
    },
  };

  it("omits the Data Model section when dataModel is absent", () => {
    const prompt = generatePrompt(baseSpec);
    expect(prompt).not.toContain("## Data Model");
  });

  it("omits the Data Model section when dataModel has no raw field", () => {
    const prompt = generatePrompt({ ...baseSpec, dataModel: {} });
    expect(prompt).not.toContain("## Data Model");
  });

  it("omits the Data Model section when dataModel.raw is empty", () => {
    const prompt = generatePrompt({ ...baseSpec, dataModel: { raw: {} } });
    expect(prompt).not.toContain("## Data Model");
  });

  it("renders a Data Model section with raw JSON", () => {
    const raw = {
      sales: [{ quarter: "Q1", revenue: 100 }],
      user: { name: "Alice" },
      totalRevenue: 220,
    };
    const prompt = generatePrompt({ ...baseSpec, dataModel: { raw } });
    expect(prompt).toContain("## Data Model");
    expect(prompt).toContain(JSON.stringify(raw, null, 2));
    expect(prompt).toContain("Use `data.<field>` to read host data.");
    expect(prompt).toContain("Array pluck works on arrays: `data.sales.revenue`");
  });

  it("includes optional description alongside raw JSON", () => {
    const prompt = generatePrompt({
      ...baseSpec,
      dataModel: { description: "Business data.", raw: { total: 42 } },
    });
    expect(prompt).toContain("Business data.");
    expect(prompt).toContain("## Data Model");
  });
});

import { describe, expect, it } from "vitest";
import { generatePrompt, type PromptSpec } from "../prompt";

describe("generatePrompt dataModel support", () => {
  const baseSpec: PromptSpec = {
    root: "Root",
    components: {
      Root: {
        signature: "Root(children: Component[])",
        description: "Root container",
      },
      Label: {
        signature: 'Label(text: string)',
        description: "Simple text label",
      },
    },
  };

  it("omits the Data Model section when dataModel is absent", () => {
    const prompt = generatePrompt(baseSpec);
    expect(prompt).not.toContain("## Data Model");
  });

  it("renders a Data Model section when dataModel metadata is present", () => {
    const prompt = generatePrompt({
      ...baseSpec,
      dataModel: {
        description: "Host business data",
        fields: {
          sales: { type: "array", description: "Quarterly sales rows" },
          user: { type: "object", description: "Current user" },
          totalRevenue: { type: "scalar", description: "Total revenue value" },
        },
      },
    });

    expect(prompt).toContain("## Data Model");
    expect(prompt).toContain("`data.sales` (array): Quarterly sales rows");
    expect(prompt).toContain("`data.user` (object): Current user");
    expect(prompt).toContain("`data.totalRevenue` (scalar): Total revenue value");
    expect(prompt).toContain("Array pluck works on arrays: `data.sales.revenue`");
  });
});

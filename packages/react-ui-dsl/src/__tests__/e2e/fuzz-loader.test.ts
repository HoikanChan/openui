import { describe, expect, it } from "vitest";
import { inferFuzzPrompt } from "./fuzz-loader";

describe("inferFuzzPrompt", () => {
  it("infers Table prompt from table- prefix", () => {
    expect(inferFuzzPrompt("table-employee-list")).toBe(
      "Show a Table for the given data",
    );
  });

  it("infers LineChart prompt from linechart- prefix", () => {
    expect(inferFuzzPrompt("linechart-bandwidth")).toBe(
      "Show a LineChart for the given data",
    );
  });

  it("infers HorizontalBarChart from hbar- shorthand", () => {
    expect(inferFuzzPrompt("hbar-interface-traffic")).toBe(
      "Show a HorizontalBarChart for the given data",
    );
  });

  it("infers Card prompt from card- prefix", () => {
    expect(inferFuzzPrompt("card-device-status")).toBe(
      "Show a Card for the given data",
    );
  });

  it("falls back to generic prompt for unknown hint (numeric id)", () => {
    expect(inferFuzzPrompt("0")).toBe(
      "Show an appropriate component for the given data",
    );
  });

  it("falls back to generic prompt for unknown hint string", () => {
    expect(inferFuzzPrompt("unknown-something")).toBe(
      "Show an appropriate component for the given data",
    );
  });

  it("is case-insensitive for the hint", () => {
    expect(inferFuzzPrompt("TABLE-employees")).toBe(
      "Show a Table for the given data",
    );
  });

  it("handles id with no dash (hint only)", () => {
    expect(inferFuzzPrompt("card")).toBe("Show a Card for the given data");
  });
});

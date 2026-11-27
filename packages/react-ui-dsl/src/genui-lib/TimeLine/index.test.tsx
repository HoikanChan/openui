import { describe, expect, it } from "vitest";
import { normalizeTimelineItems } from ".";

describe("TimeLine normalization", () => {
  const renderNode = (value: unknown) => value;

  it("normalizes structured DSL timeline items", () => {
    const items = normalizeTimelineItems(
      [
        {
          content: {
            title: "Deployed",
            children: ["ok"],
          },
          iconType: "success",
        },
      ],
      renderNode,
    );

    expect(items).toEqual([
      {
        content: ["ok"],
        iconType: "success",
        title: "Deployed",
      },
    ]);
  });

  it("normalizes simple host-data timeline items", () => {
    const items = normalizeTimelineItems(
      [
        {
          title: "v2.1.0 deployed to production",
          description: "Production deployment completed successfully.",
          status: "success",
        },
        {
          title: "v2.0.1 staged for rollout",
          description: "Rollout is pending approval.",
        },
      ],
      renderNode,
    );

    expect(items).toEqual([
      {
        content: "Production deployment completed successfully.",
        iconType: "success",
        title: "v2.1.0 deployed to production",
      },
      {
        content: "Rollout is pending approval.",
        iconType: "default",
        title: "v2.0.1 staged for rollout",
      },
    ]);
  });
});

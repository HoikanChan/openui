import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  pushVisualJudgeFeedback,
  readLangSmithRunMap,
  writeLangSmithRunMap,
} from "./langsmith-feedback.ts";
import type { JudgeScore } from "./types.ts";

describe("langsmith-feedback", () => {
  const dirs: string[] = [];

  afterEach(() => {
    while (dirs.length > 0) {
      const dir = dirs.pop();
      if (dir) rmSync(dir, { recursive: true, force: true });
    }
  });

  function makeTempDir(): string {
    const dir = mkdtempSync(join(tmpdir(), "ls-fb-"));
    dirs.push(dir);
    return dir;
  }

  it("is a no-op when LANGSMITH_API_KEY is absent", async () => {
    const dir = makeTempDir();
    writeLangSmithRunMap(dir, { sessionId: "sess-1", runs: { f1: "run-1" } });

    const res = await pushVisualJudgeFeedback({
      runDir: dir,
      scores: [],
      overallScore: 0,
      env: {},
    });

    expect(res).toEqual({ uploadedRuns: 0, uploadedScreenshots: 0, summaryUploaded: false });
  });

  it("uploads rendered screenshot multipart child run, row feedback, and session summary", async () => {
    const dir = makeTempDir();
    const pngPath = join(dir, "card-kpi.png");
    writeFileSync(pngPath, Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    writeLangSmithRunMap(dir, { sessionId: "sess-42", runs: { "card-kpi": "run-99" } });
    expect(readLangSmithRunMap(dir)?.sessionId).toBe("sess-42");

    const feedbackPosts: Array<{
      url: string;
      headers: Record<string, string>;
      body: Record<string, unknown>;
    }> = [];
    let multipartBodyText = "";
    const fakeFetch = (async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (method === "GET" && url.endsWith("/api/v1/runs/run-99")) {
        return new Response(
          JSON.stringify({
            id: "run-99",
            trace_id: "run-99",
            dotted_order: "20261128T032000000000Zrun-99",
            session_id: "sess-42",
          }),
          { status: 200 },
        );
      }
      if (method === "POST" && url.endsWith("/api/v1/runs/multipart")) {
        const raw = init?.body as Uint8Array;
        multipartBodyText = Buffer.from(raw).toString("latin1");
        return new Response(JSON.stringify({ message: "Runs batch ingested" }), { status: 202 });
      }
      feedbackPosts.push({
        url,
        headers: (init?.headers ?? {}) as Record<string, string>,
        body: JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>,
      });
      return new Response(JSON.stringify({ id: "fb-1" }), { status: 200 });
    }) as typeof fetch;

    const score: JudgeScore = {
      fixtureId: "card-kpi",
      component_fit: 3,
      data_completeness: 3,
      format_quality: 2,
      layout_coherence: 3,
      overall: 8.25,
      feedback: "clean cards, slight crowding",
      visual_issues: ["crowded"],
      screenshotPath: pngPath,
      degraded: false,
    };

    const res = await pushVisualJudgeFeedback({
      runDir: dir,
      scores: [score],
      overallScore: 8.25,
      renderResults: { "card-kpi": { passed: true } },
      env: {
        LANGSMITH_API_KEY: "ls-secret",
        LANGSMITH_ENDPOINT: "https://api.smith.langchain.com/",
        LANGSMITH_WORKSPACE_ID: "ws-7",
      },
      fetchImpl: fakeFetch,
    });

    expect(res).toEqual({ uploadedRuns: 1, uploadedScreenshots: 1, summaryUploaded: true });
    expect(multipartBodyText).toContain("visual_render_and_judge");
    expect(multipartBodyText).toContain(".rendered_page.png");
    expect(multipartBodyText).toContain("Content-Type: image/png");

    // 6 numeric metrics + visual_issues + 2 session summaries (visual_overall_avg, render_pass_rate)
    expect(feedbackPosts).toHaveLength(9);
    expect(feedbackPosts[0]?.url).toBe("https://api.smith.langchain.com/api/v1/feedback");
    expect(feedbackPosts[0]?.headers["x-api-key"]).toBe("ls-secret");
    expect(feedbackPosts[0]?.headers["X-Tenant-Id"]).toBe("ws-7");

    const overallCall = feedbackPosts.find((c) => c.body["key"] === "visual_overall");
    expect(overallCall?.body["comment"]).toBe("clean cards, slight crowding");

    const summaryAvg = feedbackPosts.find((c) => c.body["key"] === "visual_overall_avg");
    expect(summaryAvg?.body).toEqual({
      session_id: "sess-42",
      key: "visual_overall_avg",
      score: 8.25,
    });
    const summaryRender = feedbackPosts.find((c) => c.body["key"] === "render_pass_rate");
    expect(summaryRender?.body).toEqual({
      session_id: "sess-42",
      key: "render_pass_rate",
      score: 1,
    });
  });
});

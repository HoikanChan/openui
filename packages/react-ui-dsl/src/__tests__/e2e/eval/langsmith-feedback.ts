import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import type { JudgeScore } from "./types.ts";

export interface LangSmithRunMap {
  sessionId?: string;
  runs: Record<string, string>;
}

export const LANGSMITH_RUNS_FILENAME = "langsmith-runs.json";

export interface LangSmithFeedbackConfig {
  apiKey: string;
  endpoint: string;
  workspaceId?: string;
}

export interface VisualPushResult {
  uploadedRuns: number;
  uploadedScreenshots: number;
  summaryUploaded: boolean;
}

interface ParentRunMeta {
  id: string;
  trace_id: string;
  dotted_order: string;
  session_id?: string;
}

export function resolveLangSmithConfig(
  env: Record<string, string | undefined> = process.env,
): LangSmithFeedbackConfig | null {
  const apiKey = env["LANGSMITH_API_KEY"]?.trim();
  if (!apiKey) return null;
  const rawEndpoint = env["LANGSMITH_ENDPOINT"]?.trim() || "https://api.smith.langchain.com";
  const endpoint = rawEndpoint.replace(/\/+$/, "");
  const workspaceId = env["LANGSMITH_WORKSPACE_ID"]?.trim() || undefined;
  return { apiKey, endpoint, workspaceId };
}

export function writeLangSmithRunMap(runDir: string, data: LangSmithRunMap): void {
  writeFileSync(
    resolve(runDir, LANGSMITH_RUNS_FILENAME),
    JSON.stringify(data, null, 2),
    "utf-8",
  );
}

export function readLangSmithRunMap(runDir: string): LangSmithRunMap | null {
  const path = resolve(runDir, LANGSMITH_RUNS_FILENAME);
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, "utf-8")) as LangSmithRunMap;
  } catch {
    return null;
  }
}

function roundScore(score: number): number {
  if (!Number.isFinite(score)) return 0;
  return Math.round(score * 10000) / 10000;
}

function baseHeaders(config: LangSmithFeedbackConfig): Record<string, string> {
  const headers: Record<string, string> = {
    "x-api-key": config.apiKey,
  };
  if (config.workspaceId) {
    headers["X-Tenant-Id"] = config.workspaceId;
  }
  return headers;
}

function formatDottedOrderTimestamp(date: Date): string {
  const pad = (n: number, len = 2) => String(n).padStart(len, "0");
  return (
    `${date.getUTCFullYear()}${pad(date.getUTCMonth() + 1)}${pad(date.getUTCDate())}` +
    `T${pad(date.getUTCHours())}${pad(date.getUTCMinutes())}${pad(date.getUTCSeconds())}` +
    `${pad(date.getUTCMilliseconds(), 3)}000Z`
  );
}

async function postFeedback(
  config: LangSmithFeedbackConfig,
  payload: Record<string, unknown>,
  fetchImpl: typeof fetch,
): Promise<void> {
  const normalized =
    typeof payload["score"] === "number"
      ? { ...payload, score: roundScore(payload["score"]) }
      : payload;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...baseHeaders(config),
  };
  const resp = await fetchImpl(`${config.endpoint}/api/v1/feedback`, {
    method: "POST",
    headers,
    body: JSON.stringify(normalized),
  });
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    throw new Error(`LangSmith feedback HTTP ${resp.status}: ${text.slice(0, 300)}`);
  }
}

async function fetchParentRunMeta(
  config: LangSmithFeedbackConfig,
  runId: string,
  fetchImpl: typeof fetch,
): Promise<ParentRunMeta | null> {
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await fetchImpl(`${config.endpoint}/api/v1/runs/${encodeURIComponent(runId)}`, {
      method: "GET",
      headers: baseHeaders(config),
    });
    if (resp.ok) {
      const json = (await resp.json()) as Partial<ParentRunMeta>;
      if (json.trace_id && json.dotted_order) {
        return {
          id: runId,
          trace_id: json.trace_id,
          dotted_order: json.dotted_order,
          session_id: json.session_id,
        };
      }
      return null;
    }
    if (resp.status !== 404 || attempt === 2) {
      return null;
    }
    await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
  }
  return null;
}

interface MultipartPart {
  name: string;
  contentType: string;
  data: Uint8Array;
  filename?: string;
}

function buildMultipartBody(boundary: string, parts: MultipartPart[]): Uint8Array {
  const chunks: Uint8Array[] = [];
  const encoder = new TextEncoder();
  for (const part of parts) {
    const fn = part.filename ? `; filename="${part.filename}"` : "";
    const header =
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="${part.name}"${fn}\r\n` +
      `Content-Type: ${part.contentType}\r\n` +
      `Content-Length: ${part.data.byteLength}\r\n\r\n`;
    chunks.push(encoder.encode(header));
    chunks.push(part.data);
    chunks.push(encoder.encode("\r\n"));
  }
  chunks.push(encoder.encode(`--${boundary}--\r\n`));

  const totalLength = chunks.reduce((sum, c) => sum + c.byteLength, 0);
  const merged = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return merged;
}

/**
 * Attach a child `visual_render_and_judge` tool run under the parent generation/repair run,
 * carrying the rendered PNG screenshot as a LangSmith multipart attachment plus full visual scores.
 */
async function uploadVisualRenderChildRun(options: {
  config: LangSmithFeedbackConfig;
  parentRunId: string;
  sessionId?: string;
  score: JudgeScore;
  renderPassed?: boolean;
  renderError?: string;
  fetchImpl: typeof fetch;
}): Promise<{ childRunId: string; screenshotUploaded: boolean } | null> {
  const { config, parentRunId, sessionId, score, renderPassed, renderError, fetchImpl } = options;
  const parent = await fetchParentRunMeta(config, parentRunId, fetchImpl);
  if (!parent) {
    return null;
  }

  const childRunId = randomUUID();
  const now = new Date();
  const iso = now.toISOString();
  const childDottedOrder = `${parent.dotted_order}.${formatDottedOrderTimestamp(now)}${childRunId}`;
  const effectiveSessionId = sessionId ?? parent.session_id;

  const postMeta: Record<string, unknown> = {
    id: childRunId,
    trace_id: parent.trace_id,
    parent_run_id: parentRunId,
    dotted_order: childDottedOrder,
    name: "visual_render_and_judge",
    run_type: "tool",
    start_time: iso,
    end_time: iso,
    ...(effectiveSessionId ? { session_id: effectiveSessionId } : {}),
  };

  const inputsPayload = {
    fixtureId: score.fixtureId,
    renderPassed: renderPassed ?? !score.degraded,
    ...(renderError ? { renderError } : {}),
  };

  const outputsPayload = {
    overall: roundScore(score.overall),
    component_fit: roundScore(score.component_fit),
    data_completeness: roundScore(score.data_completeness),
    format_quality: roundScore(score.format_quality),
    layout_coherence: roundScore(score.layout_coherence),
    feedback: score.feedback ?? "",
    visual_issues: score.visual_issues ?? [],
    degraded: score.degraded,
    hasScreenshot: Boolean(score.screenshotPath && existsSync(score.screenshotPath)),
  };

  const encoder = new TextEncoder();
  const parts: MultipartPart[] = [
    {
      name: `post.${childRunId}`,
      contentType: "application/json",
      data: encoder.encode(JSON.stringify(postMeta)),
    },
    {
      name: `post.${childRunId}.inputs`,
      contentType: "application/json",
      data: encoder.encode(JSON.stringify(inputsPayload)),
    },
    {
      name: `post.${childRunId}.outputs`,
      contentType: "application/json",
      data: encoder.encode(JSON.stringify(outputsPayload)),
    },
  ];

  let screenshotUploaded = false;
  if (score.screenshotPath && existsSync(score.screenshotPath)) {
    const pngBytes = readFileSync(score.screenshotPath);
    if (pngBytes.byteLength > 0) {
      parts.push({
        name: `attachment.${childRunId}.rendered_page.png`,
        contentType: "image/png",
        data: new Uint8Array(pngBytes.buffer, pngBytes.byteOffset, pngBytes.byteLength),
        filename: `${score.fixtureId}.png`,
      });
      screenshotUploaded = true;
    }
  }

  const boundary = `----LangSmithVisual${randomUUID().replace(/-/g, "")}`;
  const body = buildMultipartBody(boundary, parts);
  const resp = await fetchImpl(`${config.endpoint}/api/v1/runs/multipart`, {
    method: "POST",
    headers: {
      ...baseHeaders(config),
      "Content-Type": `multipart/form-data; boundary=${boundary}`,
    },
    body: Buffer.from(body),
  });
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    throw new Error(`LangSmith multipart HTTP ${resp.status}: ${text.slice(0, 300)}`);
  }
  return { childRunId, screenshotUploaded };
}

/**
 * Push Playwright rendered screenshots (as multipart run attachments) and VLM visual judge scores
 * onto the matching LangSmith runs recorded during Java generation/repair, plus session summaries.
 * No-op when LANGSMITH_API_KEY is unset or no run map exists.
 */
export async function pushVisualJudgeFeedback(options: {
  runDir: string;
  scores: JudgeScore[];
  overallScore: number;
  renderResults?: Record<string, { passed: boolean; error?: string }>;
  env?: Record<string, string | undefined>;
  fetchImpl?: typeof fetch;
}): Promise<VisualPushResult> {
  const config = resolveLangSmithConfig(options.env);
  if (!config) {
    return { uploadedRuns: 0, uploadedScreenshots: 0, summaryUploaded: false };
  }
  const runMap = readLangSmithRunMap(options.runDir);
  if (!runMap || Object.keys(runMap.runs).length === 0) {
    return { uploadedRuns: 0, uploadedScreenshots: 0, summaryUploaded: false };
  }
  const fetchImpl = options.fetchImpl ?? fetch;
  let uploadedRuns = 0;
  let uploadedScreenshots = 0;
  let renderPassedCount = 0;

  for (const score of options.scores) {
    const runId = runMap.runs[score.fixtureId];
    if (!runId) continue;

    const renderInfo = options.renderResults?.[score.fixtureId];
    const renderPassed = renderInfo ? renderInfo.passed : !score.degraded;
    if (renderPassed) renderPassedCount++;

    try {
      const child = await uploadVisualRenderChildRun({
        config,
        parentRunId: runId,
        sessionId: runMap.sessionId,
        score,
        renderPassed,
        renderError: renderInfo?.error,
        fetchImpl,
      });
      if (child?.screenshotUploaded) {
        uploadedScreenshots++;
      }
    } catch (err) {
      console.warn(
        `[langsmith] Failed to attach rendered screenshot child run for ${score.fixtureId}: ${String(err)}`,
      );
    }

    const metrics: Array<[string, number]> = [
      ["render_passed", renderPassed ? 1 : 0],
      ["visual_component_fit", score.component_fit],
      ["visual_data_completeness", score.data_completeness],
      ["visual_format_quality", score.format_quality],
      ["visual_layout_coherence", score.layout_coherence],
      ["visual_overall", score.overall],
    ];
    for (const [key, value] of metrics) {
      await postFeedback(
        config,
        {
          run_id: runId,
          ...(runMap.sessionId ? { session_id: runMap.sessionId } : {}),
          key,
          score: value,
          ...(key === "visual_overall" && score.feedback ? { comment: score.feedback } : {}),
          ...(key === "render_passed" && renderInfo?.error ? { comment: renderInfo.error } : {}),
        },
        fetchImpl,
      );
    }
    const visualIssues = score.visual_issues ?? [];
    if (visualIssues.length > 0) {
      await postFeedback(
        config,
        {
          run_id: runId,
          ...(runMap.sessionId ? { session_id: runMap.sessionId } : {}),
          key: "visual_issues",
          value: visualIssues.join(","),
          ...(score.screenshotPath ? { comment: `screenshot: ${score.screenshotPath}` } : {}),
        },
        fetchImpl,
      );
    }
    uploadedRuns++;
  }

  let summaryUploaded = false;
  if (runMap.sessionId && uploadedRuns > 0) {
    await postFeedback(
      config,
      {
        session_id: runMap.sessionId,
        key: "visual_overall_avg",
        score: options.overallScore,
      },
      fetchImpl,
    );
    await postFeedback(
      config,
      {
        session_id: runMap.sessionId,
        key: "render_pass_rate",
        score: renderPassedCount / uploadedRuns,
      },
      fetchImpl,
    );
    summaryUploaded = true;
  }

  return { uploadedRuns, uploadedScreenshots, summaryUploaded };
}

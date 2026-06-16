// GenUI Service(examples/genui-service,Java)REST 客户端。
// 契约见 examples/genui-service/src/main/resources/swagger/genui-service.yaml。

const API_BASE = "http://localhost:3001/v1";

export const GENERATE_URL = `${API_BASE}/generate`;

export interface ContextSummary {
  contextId: string;
  version?: string;
  componentCount?: number;
  toolCount?: number;
}

export async function listContexts(): Promise<ContextSummary[]> {
  const res = await fetch(`${API_BASE}/contexts`);
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  return (await res.json()) as ContextSummary[];
}

/** 拉取服务端(Java SDK)拼装的 system prompt;demo 的 prompt tab 以此为默认值。 */
export async function assemblePrompt(
  contextId?: string,
  dataModel?: Record<string, unknown>,
): Promise<string> {
  const res = await fetch(`${API_BASE}/prompts/assemble`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contextId: contextId || undefined,
      dataModel: dataModel ? { raw: dataModel } : undefined,
    }),
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const json = (await res.json()) as { prompt: string };
  return json.prompt;
}

/**
 * Renderer 的 toolProvider:把生成 DSL 中 Query/Mutation 节点的工具调用
 * 转发到 GenUI Service 的工具执行端点。
 *
 * 采用 MCP 客户端形态(callTool({name, arguments})),Renderer 会用
 * extractToolResult 解包 —— structuredContent 原样透出为 Query 的值。
 */
export const serviceToolProvider = {
  async callTool({ name, arguments: args }: { name: string; arguments?: Record<string, unknown> }) {
    const res = await fetch(`${API_BASE}/tools/${encodeURIComponent(name)}/execute`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(args ?? {}),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `HTTP ${res.status}`);
    }
    return { content: [], structuredContent: (await res.json()) as unknown };
  },
};

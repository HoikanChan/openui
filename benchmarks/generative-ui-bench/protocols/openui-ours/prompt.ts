import { callJavaBridge } from "./bridge.ts";
import type { Brief } from "../../briefs/briefs.ts";

export type AssembledPrompt = {
  systemPrompt: string;
  userPrompt: string;
  contractVersion: string;
  hasDataModel: boolean;
};

type AssembleReqItem = {
  name: string;
  prompt: string;
  dataModel: Record<string, unknown> | null;
};

export function assemblePromptsBatch(
  items: { brief: Brief; dataModel: Record<string, unknown> | null }[],
): Map<string, AssembledPrompt> {
  const req: AssembleReqItem[] = items.map(({ brief, dataModel }) => ({
    name: brief.name,
    prompt: brief.prompt,
    dataModel,
  }));
  const res = callJavaBridge<AssembleReqItem[], Record<string, AssembledPrompt>>(
    "assemble-prompts",
    req,
  );
  return new Map(Object.entries(res));
}

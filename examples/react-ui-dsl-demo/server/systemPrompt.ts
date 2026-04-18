import { dslLibrary } from "@openuidev/react-ui-dsl";

export function buildSystemPrompt(dataModel?: Record<string, unknown>): string {
  if (!dataModel || Object.keys(dataModel).length === 0) {
    return dslLibrary.prompt();
  }
  return dslLibrary.prompt({ dataModel: { raw: dataModel } });
}

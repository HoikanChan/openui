import { callJavaBridge } from "./bridge.ts";

export type BenchError = { cls: string; detail: string };

export type RawValidationIssue = {
  code: string;
  severity: string;
  source: string;
  message: string;
  component: string | null;
  path: string | null;
};

export type OursVerdict = {
  id: string;
  renderable: boolean;
  complete: boolean;
  n: number;
  errs: BenchError[];
  rawIssues: RawValidationIssue[];
};

export type ValidateBatchItem = {
  id: string;
  text: string;
  dataModel: Record<string, unknown> | null;
  truncated: boolean;
  reqs: number;
};

export function evaluateOursBatch(items: ValidateBatchItem[]): Map<string, OursVerdict> {
  if (items.length === 0) return new Map();
  const res = callJavaBridge<ValidateBatchItem[], OursVerdict[]>("validate-batch", items);
  return new Map(res.map((r) => [r.id, r]));
}

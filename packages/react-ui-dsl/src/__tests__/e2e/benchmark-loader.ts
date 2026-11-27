import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

export interface BenchmarkCase {
  id: string;
  prompt: string;
  dataModel: unknown;
  evalHints: string[];
  taxonomy: string[];
}

interface BenchmarkEnvelope {
  meta: {
    prompt?: string;
    evalHints?: string[];
    taxonomy?: string[];
  };
  data: unknown;
}

export function loadBenchmarkCases(dir: string): BenchmarkCase[] {
  let files: string[];
  try {
    files = readdirSync(dir).filter((f) => f.endsWith(".json"));
  } catch {
    return [];
  }

  return files.map((filename) => {
    const id = filename.replace(/\.json$/, "");
    const raw = JSON.parse(readFileSync(resolve(dir, filename), "utf-8")) as BenchmarkEnvelope;
    const { meta, data } = raw;
    if (!meta.prompt) {
      throw new Error(`Benchmark case "${id}" is missing meta.prompt`);
    }
    return {
      id,
      prompt: meta.prompt,
      dataModel: data,
      evalHints: meta.evalHints ?? [],
      taxonomy: meta.taxonomy ?? [],
    };
  });
}

// Verifies that all 46 upstream briefs have a valid, non-empty JSON object
// dataModel and that our Java SDK's GenerationSdk.assemblePrompt (DataProfiler
// + shapeSidecar + InsightPromptAugmenter + PromptAssembler) succeeds on every one.
import { SCENARIOS } from "../briefs/briefs.ts";
import { loadDataModels } from "../briefs/data-models.ts";
import { assemblePromptsBatch } from "../protocols/openui-ours/prompt.ts";

const dataModels = loadDataModels();
const briefNames = new Set(SCENARIOS.map((b) => b.name));
const dmNames = new Set(Object.keys(dataModels));

const missing = [...briefNames].filter((n) => !dmNames.has(n));
const extra = [...dmNames].filter((n) => !briefNames.has(n));

if (missing.length > 0 || extra.length > 0) {
  console.error("Data model key mismatch!", { missing, extra });
  process.exit(1);
}

for (const b of SCENARIOS) {
  const dm = dataModels[b.name];
  if (!dm || typeof dm !== "object" || Array.isArray(dm) || Object.keys(dm).length === 0) {
    console.error(`Invalid or empty dataModel for ${b.name}`);
    process.exit(1);
  }
}

const assembled = assemblePromptsBatch(
  SCENARIOS.map((brief) => ({ brief, dataModel: dataModels[brief.name] })),
);

let minLen = Infinity;
let maxLen = 0;
let sumLen = 0;
let withSidecar = 0;
for (const b of SCENARIOS) {
  const entry = assembled.get(b.name);
  if (!entry || !entry.hasDataModel || !entry.systemPrompt.includes("## Data Model")) {
    console.error(`Java SDK prompt missing Data Model section for ${b.name}`);
    process.exit(1);
  }
  const len = entry.systemPrompt.length;
  minLen = Math.min(minLen, len);
  maxLen = Math.max(maxLen, len);
  sumLen += len;
  if (entry.systemPrompt.includes("Data shape (full dataset):")) {
    withSidecar++;
  }
}

console.log(
  `OK: all ${SCENARIOS.length}/46 briefs have valid Data Models and assemble cleanly via Java SDK.`,
);
console.log(
  `Prompt chars: min=${minLen}, avg=${Math.round(sumLen / SCENARIOS.length)}, max=${maxLen}, DataProfiler sidecar triggered=${withSidecar}/46`,
);

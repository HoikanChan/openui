import { z } from "zod";
import type { CapabilityGap } from "./capability-gaps";
import { defaultCapabilityGaps, validateCapabilityGaps } from "./capability-gaps";

const SourceSchema = z
  .object({
    contractVersion: z.string().min(1),
    components: z.record(z.string(), z.unknown()),
    builtins: z.array(z.object({ name: z.string().min(1) }).passthrough()).optional(),
  })
  .passthrough();

/**
 * Export a renderer-free contract without rebuilding or stripping structured
 * builtin rules. Omitted gaps use defaults; an explicit array is authoritative.
 * Validation completes before a caller writes any generated artifact.
 */
export function exportGenerationContract<T extends object>(
  source: T,
): Omit<T, "capabilityGaps"> & { capabilityGaps: CapabilityGap[] } {
  const parsed = SourceSchema.parse(source);
  const capabilityGaps = validateCapabilityGaps(
    parsed.capabilityGaps === undefined ? defaultCapabilityGaps() : parsed.capabilityGaps,
    Object.keys(parsed.components),
    (parsed.builtins ?? []).map((builtin) => builtin.name),
  );
  return { ...source, capabilityGaps };
}

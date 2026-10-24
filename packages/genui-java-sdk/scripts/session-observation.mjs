// The frontend side must be an observation from Renderer, never an independent parse.
export function compareRendererObservation(observation, finalResponse, backendParse) {
  const reason = !observation?.parseResult ? 'renderer-callback-missing'
    : observation.response !== finalResponse ? 'renderer-callback-stale' : null;
  const front = reason ? null : observation.parseResult;
  return {
    frontendObservationAvailable: reason === null,
    frontendObservationReason: reason,
    frontendObservedResponse: observation?.response ?? null,
    frontendBackendConsistent: front ? JSON.stringify(front.root) === JSON.stringify(backendParse.root) : null,
    frontendRoot: front?.root ?? null,
    frontendErrors: front?.meta.errors ?? null,
    backendRoot: backendParse.root,
    backendDefinitionErrors: backendParse.meta.errors,
  };
}

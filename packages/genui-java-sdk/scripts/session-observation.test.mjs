import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compareRendererObservation } from './session-observation.mjs';

const root={type:'element',typeName:'Stack',props:{children:[]}};
const parsed={root,meta:{errors:[]}};
test('missing actual Renderer callback cannot certify matching empty parser roots',()=>{
 const result=compareRendererObservation(null,'',parsed);
 assert.equal(result.frontendObservationAvailable,false);
 assert.equal(result.frontendBackendConsistent,null);
 assert.equal(result.frontendObservationReason,'renderer-callback-missing');
 assert.equal(result.frontendRoot,null);
});
test('callback for an older response cannot certify the final response even if roots match',()=>{
 const observation={response:'root = Stack([]);',parseResult:parsed};
 const result=compareRendererObservation(observation,'root = Stack([]);\nroot = Stack([TextContent("final")]);',parsed);
 assert.equal(result.frontendObservationAvailable,false);
 assert.equal(result.frontendBackendConsistent,null);
 assert.equal(result.frontendObservationReason,'renderer-callback-stale');
});
test('only actual callback describing final response can certify root equality',()=>{
 const result=compareRendererObservation({response:'final DSL',parseResult:parsed},'final DSL',parsed);
 assert.equal(result.frontendObservationAvailable,true);
 assert.equal(result.frontendBackendConsistent,true);
 assert.equal(result.frontendObservationReason,null);
 assert.deepEqual(result.frontendRoot,root);
});

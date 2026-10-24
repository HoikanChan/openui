import { test } from 'node:test';
import assert from 'node:assert/strict';
import { qualityReport, resultHash } from './session-quality.mjs';

test('valid DSL with incorrect rendered values remains wrong and pending truth excluded',()=>{
 const value={caseId:'units',validationPassed:true,expectation:{state:'pending-human-confirmation'}};
 const raw=JSON.stringify(value);
 const a={caseId:'units',category:'still-wrong',assessor:'reviewer',renderEvidence:'units.png',reason:'1048576 labeled MiB rather than1',resultSha256:resultHash(raw)};
 const report=qualityReport([{raw,value}],[a]);
 assert.equal(report.validationPassCount,1);assert.equal(report.provisionalQualityCounts['still-wrong'],1);
 assert.equal(report.accuracyDenominator,0);assert.equal(report.correctFullRate,null);
});
test('confirmed truth reports explicit alternatives separately and rejects stale assessments',()=>{
 const value={caseId:'topn',validationPassed:true,expectation:{state:'human-confirmed',confirmationEvidence:'user-message'}};
 const raw=JSON.stringify(value);
 const a={caseId:'topn',category:'explicit-alternative-or-markdown',assessor:'reviewer',renderEvidence:'topn.png',reason:'All sorted rows with visible limitation',resultSha256:resultHash(raw)};
 const report=qualityReport([{raw,value}],[a]);assert.equal(report.accuracyDenominator,1);
 assert.equal(report.correctFullRate,0);assert.equal(report.explicitAlternativeRate,1);
 assert.throws(()=>qualityReport([{raw:raw+' ',value}],[a]),/Stale/);
});

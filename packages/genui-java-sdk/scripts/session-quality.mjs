#!/usr/bin/env node
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';

const categories = ['correct-full', 'explicit-alternative-or-markdown', 'still-wrong'];
export const resultHash = text => createHash('sha256').update(text).digest('hex');
// Assessment is deliberately separate from SDK validation. Humans confirm the truth packet;
// a named assessor then evaluates the actual rendered output against it, attaching evidence.
export function qualityReport(results, assessments) {
  const counts = Object.fromEntries(categories.map(category=>[category,0]));
  const provisional = {...counts};
  const seen = new Set();
  for(const a of assessments){
    if(seen.has(a.caseId))throw Error('Duplicate assessment: '+a.caseId);seen.add(a.caseId);
    if(!categories.includes(a.category)||!a.assessor||!a.renderEvidence||!a.reason)throw Error('Incomplete assessment: '+a.caseId);
    const record=results.find(r=>r.value.caseId===a.caseId);
    if(!record||a.resultSha256!==resultHash(record.raw))throw Error('Stale or missing result evidence: '+a.caseId);
  }
  const cases=results.map(({value,raw})=>{
    const assessment=assessments.find(a=>a.caseId===value.caseId);
    const confirmed=value.expectation?.state==='human-confirmed' && Boolean(value.expectation?.confirmationEvidence);
    const eligible=Boolean(confirmed && assessment);
    if(assessment)provisional[assessment.category]++;
    if(eligible)counts[assessment.category]++;
    return {caseId:value.caseId,validationPassed:value.validationPassed,qualityCategory:assessment?.category||'unconfirmed',accuracyEligible:eligible,
      excludedReason:eligible?null:!confirmed?'input, intent and expected result await human confirmation':'rendered output awaits assessment',resultSha256:resultHash(raw),assessment:assessment||null};
  });
  const denominator=Object.values(counts).reduce((a,b)=>a+b,0);
  return {validationPassCount:results.filter(r=>r.value.validationPassed).length,total:results.length,qualityCounts:counts,provisionalQualityCounts:provisional,
    accuracyDenominator:denominator,correctFullRate:denominator?counts['correct-full']/denominator:null,
    explicitAlternativeRate:denominator?counts['explicit-alternative-or-markdown']/denominator:null,
    excludedCount:results.length-denominator,cases};
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const [dir,file]=process.argv.slice(2);if(!dir||!file)throw Error('session-quality.mjs <run-dir> <assessments.json>');
  const results=readdirSync(dir).filter(n=>n.endsWith('.json')).map(n=>{const raw=readFileSync(join(dir,n),'utf8');return {raw,value:JSON.parse(raw)};}).filter(r=>r.value.type==='result');
  const report=qualityReport(results,JSON.parse(readFileSync(file,'utf8')));
  writeFileSync(join(dir,'quality.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({total:report.total,qualityCounts:report.qualityCounts,provisionalQualityCounts:report.provisionalQualityCounts,accuracyDenominator:report.accuracyDenominator}));
}

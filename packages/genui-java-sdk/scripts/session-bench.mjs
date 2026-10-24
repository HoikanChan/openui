#!/usr/bin/env node
// Actual Java streaming session + existing React Renderer. No TypeScript generation or Java evaluator.
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { createHash } from 'node:crypto';

const options = Object.fromEntries(process.argv.slice(2).map(arg => {
  const at = arg.indexOf('='); if (at < 3 || !arg.startsWith('--')) throw Error('Use --key=value');
  return [arg.slice(2, at), arg.slice(at + 1)];
}));
for (const key of ['renderer-root', 'dependency-root', 'env-file', 'cases', 'out']) {
  if (!options[key]) throw Error(`Missing --${key}=...; --cases must point at real repair-cases packs`);
}
const sdk = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(sdk, '../..');
const renderer = resolve(options['renderer-root']);
const deps = resolve(options['dependency-root']);
const out = resolve(options.out);
const casesRoot = resolve(options.cases);
const required = [join(renderer,'packages/react-lang/src/Renderer.tsx'),join(renderer,'packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx'),join(deps,'package.json'),join(sdk,'target/session-bench-classpath.txt')];
for (const path of required) if (!existsSync(path)) throw Error(`Required existing renderer/dependency/build artifact absent: ${path}`);
mkdirSync(out,{recursive:true});
const require = createRequire(join(deps,'package.json'));
const { createServer } = require('vite');
const { chromium } = require('playwright');
const treeHash = dir => { const rows=[]; const visit=(base,rel='')=>{for(const name of readdirSync(base,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){const part=join(rel,name.name);if(name.isDirectory())visit(join(base,name.name),part);else if(/\.(ts|tsx|java|json)$/.test(name.name))rows.push(part+':'+hash(readFileSync(join(base,name.name))));}};visit(dir);return hash(rows.join('\n'));};
const hash = text => createHash('sha256').update(text).digest('hex');
const callout = options['text-callout'] === 'true' ? join(renderer,'packages/react-ui/src/genui-lib/TextCallout/index.tsx') : null;
if(callout && !existsSync(callout))throw Error('Real TextCallout definition missing: '+callout);
const currentContractPath = join(sdk,'src/main/resources/openui/base-contract.json');
const consumerContractPath = join(renderer,'packages/react-ui-dsl/generated/base-contract.json');
const currentContract = JSON.parse(readFileSync(currentContractPath,'utf8'));
const consumerContract = JSON.parse(readFileSync(consumerContractPath,'utf8'));
const provenance = {
  rendererRoot: renderer, dependencyRoot: deps, sdkRoot: sdk,
  rendererSha256: hash(readFileSync(required[0])), librarySha256: hash(readFileSync(required[1])),
  langCoreSourceTreeSha256:treeHash(join(repo,'packages/lang-core/src')),
  sdkSourceTreeSha256:treeHash(join(sdk,'src/main/java')), benchSourceTreeSha256:treeHash(join(sdk,'src/test/java/com/huawei/cloudsop/genui/bench')),
  driverSha256:hash(readFileSync(fileURLToPath(import.meta.url))),nodeVersion:process.version,
  sdkContractFileSha256: hash(readFileSync(currentContractPath)), consumerContractFileSha256: hash(readFileSync(consumerContractPath)),
  consumerContractVersion: consumerContract.contractVersion,
  adapter: callout ? {kind:'mixed-source-library-extension', source:callout, sha256:hash(readFileSync(callout)), styling:'Unstyled original React TextCallout; source SCSS dependency cssUtils absent in checkout. No full1.1.6 parity claim.'} : null,
  sdkComponentsAbsentFromConsumer: Object.keys(currentContract.components).filter(name=>!consumerContract.components[name]),
  clock: 'Java System.currentTimeMillis immediately before public generateStream; browser Date.now after two animation frames showing nonempty visible text (same host wall clock). Node Date.now before stdin start handshake also recorded; JVM/browser warmup excluded. Java round durations use nanoTime. RAF observation does not hold next delta.',
  modelNetworkConditions: { endpoint: 'Configured LLM_BASE_URL (not copied into artifacts)', proxyConfigured: Boolean(process.env.HTTPS_PROXY || process.env.HTTP_PROXY), concurrency:1, temperature:0, thinking:false },
};
const cases = readdirSync(casesRoot).filter(name=>existsSync(join(casesRoot,name,'case.json'))).sort().map(name=>({dir:name,...JSON.parse(readFileSync(join(casesRoot,name,'case.json'),'utf8'))})).filter(c=>!options.ids || options.ids.split(',').includes(c.id));
if (!cases.length) throw Error('No matching cases');
writeFileSync(join(out,'index.html'),'<html><body><div id="app" data-fixture-id="live"><div class="preview-shell"></div></div><script type="module" src="/main.tsx"></script></body></html>');
writeFileSync(join(out,'main.tsx'), `
import React from 'react';
import {createRoot} from 'react-dom/client';
import {Renderer} from ${JSON.stringify(required[0])};
import {dslLibrary as baseLibrary} from ${JSON.stringify(required[1])};
${callout ? 'import {TextCallout} from '+JSON.stringify(callout)+';' : ''}
const dslLibrary=${callout ? 'baseLibrary.extend({components:[TextCallout]})' : 'baseLibrary'};
import {createParser,createStreamingParser} from '@openuidev/lang-core';
import {compareRendererObservation} from ${JSON.stringify(join(sdk,'scripts/session-observation.mjs'))};
const el=document.querySelector('.preview-shell');const root=createRoot(el);
let actualObservation=null;let response='', data={}, streaming=true, firstPaintEpochMs=null, firstPaintText=null, transitions=[], parser;
const full=createParser(dslLibrary.toJSONSchema(),'Stack',{externalRefs:['data']});
const settle=()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
const render=()=>{const renderedResponse=response;root.render(<Renderer library={dslLibrary} response={response} dataModel={data} isStreaming={streaming} onParseResult={r=>actualObservation={response:renderedResponse,parseResult:r}}/>);requestAnimationFrame(()=>requestAnimationFrame(()=>{
 const visible=[...el.querySelectorAll('*')].some(n=>n.getBoundingClientRect().width>0 && n.getBoundingClientRect().height>0 && getComputedStyle(n).visibility!=='hidden' && n.textContent.trim());
 if(firstPaintEpochMs===null && visible && el.innerText.trim()){firstPaintEpochMs=Date.now();firstPaintText=el.innerText;}}));};
window.begin=async(input)=>{actualObservation=null;response='';data=input;streaming=true;firstPaintEpochMs=null;firstPaintText=null;transitions=[];parser=createStreamingParser(dslLibrary.toJSONSchema(),'Stack',{externalRefs:['data']});render();await settle();};
window.consume=async(e)=>{if(e.type==='dataModel')data=e.content;
 if(e.type==='replace'){response='';parser=createStreamingParser(dslLibrary.toJSONSchema(),'Stack',{externalRefs:['data']});}
 if(e.type==='dsl'){response+=e.content;parser.push(e.content);}
 if(e.type==='done')streaming=false;
 render();
 if(e.type==='replace'||e.type==='done')transitions.push({type:e.type,epochMs:Date.now(),text:el.innerText});};
window.finish=async(result)=>{streaming=false;render();await settle();const back=full.parse(result.outputDsl);const comparison=compareRendererObservation(actualObservation,response,back);
 return {firstPaintEpochMs,firstPaintText,finalText:el.innerText,renderedHtml:el.innerHTML,response,transitions,
 ...comparison,
 rows:[...el.querySelectorAll('tbody tr')].map(n=>n.innerText)};};
window.consumerReady=true;
`);
const pkg=JSON.parse(readFileSync(join(renderer,'packages/react-ui-dsl/package.json'),'utf8'));
const externals=new Set(['react','react-dom','zod','antd','clsx',...Object.keys(pkg.dependencies||{}),...Object.keys(pkg.peerDependencies||{})]);
const aliases=[
 {find:'@openuidev/lang-core',replacement:join(repo,'packages/lang-core/src/index.ts')},
 {find:'@cloudsop/openui-lang-core',replacement:join(repo,'packages/lang-core/src/index.ts')},
 {find:'@openuidev/react-lang',replacement:join(renderer,'packages/react-lang/src/index.ts')},
];
for(const name of externals){
 if(name.startsWith('@openuidev/')||name.startsWith('@cloudsop/'))continue;
 try{aliases.push({find:name,replacement:dirname(require.resolve(name+'/package.json'))});}
 catch{try{aliases.push({find:name,replacement:require.resolve(name)});}catch{throw Error('External dependency absent: '+name);}}
}
const server=await createServer({root:out,configFile:false,logLevel:'error',resolve:{alias:aliases},server:{host:'127.0.0.1',port:0,fs:{allow:[out,repo,renderer,deps]}}});
await server.listen();
const browser=await chromium.launch({headless:true});provenance.browserVersion=browser.version();
const results=[];
try{
 for(const benchCase of cases){
  const page=await browser.newPage(); const pageErrors=[];
  page.on('pageerror',e=>pageErrors.push(e.message));
  await page.goto('http://127.0.0.1:'+server.httpServer.address().port);
  await page.waitForFunction(()=>window.consumerReady,{timeout:60000});
  await page.evaluate(data=>window.begin(data),benchCase.dataModel);
  const cp=join(sdk,'target/test-classes')+':'+join(sdk,'target/classes')+':'+readFileSync(join(sdk,'target/session-bench-classpath.txt'),'utf8').trim();
  const child=spawn('java',['-cp',cp,'com.huawei.cloudsop.genui.bench.SessionBenchCli',casesRoot,benchCase.id,resolve(options['env-file']),options.mode||'live-initial'],{cwd:repo,stdio:['pipe','pipe','pipe']});
  let stderr='',startedEpochMs,sessionResult,chain=Promise.resolve();const events=[];
  child.stderr.on('data',chunk=>stderr+=chunk.toString());
  const exit=new Promise((res,rej)=>{child.on('error',rej);child.on('close',code=>code===0?res():rej(Error('Java session exited '+code+': '+stderr.slice(-2000))));});
  const timeout=setTimeout(()=>child.kill('SIGKILL'),120000);
  const lines=createInterface({input:child.stdout});
  for await (const line of lines){
   const event=JSON.parse(line);
   if(event.type==='ready'){startedEpochMs=Date.now();child.stdin.end('start\n');continue;}
   if(event.type==='result'){sessionResult=event;continue;}
   events.push({...event,receivedEpochMs:Date.now()});
   chain=chain.then(()=>page.evaluate(e=>window.consume(e),event));
  }
  await exit;clearTimeout(timeout);await chain;
  if(!sessionResult)throw Error('Missing Java result');
  const rendered=await page.evaluate(result=>window.finish(result),sessionResult);
  const firstVisibleRenderMs=rendered.firstPaintEpochMs===null?null:rendered.firstPaintEpochMs-sessionResult.publicRequestEnteredEpochMs;
  const record={...sessionResult,...rendered,events,startedEpochMs,firstVisibleRenderMs,
   firstVisibleFromClientStartMs:rendered.firstPaintEpochMs===null?null:rendered.firstPaintEpochMs-startedEpochMs,
   firstVisibleWithin3s:sessionResult.generationMode==='live-initial' ? firstVisibleRenderMs!==null && firstVisibleRenderMs<=3000 : null,
   firstVisibleDslFragmentMs:events.find(e=>e.type==='dsl')?.elapsedMs ?? null,
   pageErrors, expectation:benchCase.expectation||{state:'unconfirmed'},provenance,
   qualityCategory:'unconfirmed',accuracyEligible:false};
  writeFileSync(join(out,benchCase.id+'.json'),JSON.stringify(record,null,2));
  await page.screenshot({path:join(out,benchCase.id+'.png'),fullPage:true});
  results.push(record); console.log(JSON.stringify({id:record.caseId,endReason:record.endReason,validationPassed:record.validationPassed,firstVisibleRenderMs,consistent:record.frontendBackendConsistent,requests:record.modelRequestCount,pageErrors}));
  await page.close();
 }
 const summary={generatedAt:new Date().toISOString(),generationMode:options.mode||'live-initial',total:results.length,validationPassCount:results.filter(r=>r.validationPassed).length,
  quality:{correctFull:0,explicitAlternativeOrMarkdown:0,stillWrong:0,unconfirmed:results.length,accuracyDenominator:0,accuracy:null},
  firstVisibleWithin3sCount:results.filter(r=>r.firstVisibleWithin3s).length,frontendBackendConsistentCount:results.filter(r=>r.frontendBackendConsistent).length,
  cases:results.map(r=>({id:r.caseId,endReason:r.endReason,firstVisibleRenderMs:r.firstVisibleRenderMs,dslSessionMs:r.dslSessionMs,validationPassed:r.validationPassed})),provenance};
 writeFileSync(join(out,'summary.json'),JSON.stringify(summary,null,2));
}finally{await browser.close();await server.close();}

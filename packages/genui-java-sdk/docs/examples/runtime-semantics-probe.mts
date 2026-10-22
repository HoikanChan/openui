// #79 contract evidence only; this does not execute DSL or assert runtime behavior.
// Run from repo root: pnpm exec tsx packages/genui-java-sdk/docs/examples/runtime-semantics-probe.mts
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
const bytes = readFileSync(new URL('../../src/main/resources/openui/base-contract.json', import.meta.url));
const contract = JSON.parse(bytes.toString('utf8'));
const expectedNames = ['Count','First','Last','Sum','Avg','Min','Max','Sort','Filter','Flatten','ObjectEntries','ObjectKeys','ObjectValues','Round','Abs','Floor','Ceil','Number','Switch','FormatDate','FormatBytes','FormatNumber','FormatPercent','FormatDuration','MergeObject','Join','String','Each','Render'];
assert.deepEqual(contract.builtins.map((b: any) => b.name), expectedNames);
for (const builtin of contract.builtins) {
  assert.ok(Array.isArray(builtin.params), builtin.name);
  const min = builtin.params.filter((p: any) => !p.optional && !p.variadic).length;
  const max = builtin.params.some((p: any) => p.variadic) ? 'unbounded' : builtin.params.length;
  console.log(JSON.stringify({name: builtin.name, arity: [min, max], params: builtin.params, returnType: builtin.returnType ?? 'binder-special', description: builtin.description}));
}
const byName = Object.fromEntries(contract.builtins.map((b: any) => [b.name, b]));
assert.equal(byName.MergeObject.params.filter((p: any) => !p.optional && !p.variadic).length, 2);
assert.match(byName.MergeObject.description, /Non-object arguments are skipped/);
assert.match(byName.MergeObject.description, /Returns \{\} when no objects are provided/);
assert.equal(byName.Flatten.params[0].type, 'array[]');
assert.match(byName.Flatten.description, /Returns \[\] for null and non-array input/);
assert.equal(byName.First.returnType, 'T');
assert.equal(byName.Last.returnType, 'T');
for (const name of ['Sum','Avg','Min','Max']) assert.equal(byName[name].returnType, 'number');
assert.equal(byName.FormatNumber.returnType, 'string');
assert.match(byName.FormatNumber.description, /returns raw input for invalid values/);
assert.match(byName.Each.description, /Do NOT create a separate statement/);
const acceptance = JSON.parse(readFileSync(new URL('./contract-migration-acceptance.json', import.meta.url), 'utf8'));
assert.equal(acceptance.contractVersion, contract.contractVersion);
assert.equal(new Set(acceptance.cases.map((c: any) => c.id)).size, acceptance.cases.length);
for (const sample of acceptance.cases) {
  assert.ok(sample.source, sample.id);
  if (sample.preciseResult?.startsWith('unresolved')) {
    assert.ok(!Object.hasOwn(sample, 'expectedValue'), sample.id);
  }
}
console.log(JSON.stringify({acceptanceCases: acceptance.cases.length, uniqueIds: true, unresolvedCasesHaveNoExpectedValue: true}));
console.log(JSON.stringify({contractVersion: contract.contractVersion, builtins: expectedNames.length, sha256: createHash('sha256').update(bytes).digest('hex'), evidence: 'contract-only; no runtime execution'}));

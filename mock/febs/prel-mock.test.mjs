/**
 * Prel Mock 功能测试
 * 运行： node --test prel-mock.test.mjs
 */
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import Prel from './prel-mock.mjs';

beforeEach(() => Prel.__reset());

// ---------------- define / assets / autoLoad ----------------
test('define 声明资源，assets 可查询', () => {
  Prel.define({
    piuA: { version: '1.2.3', js: ['a.js'], css: ['a.css'] },
    piuB: { version: '2.0.0', js: ['b1.js', 'b2.js'] },
  });
  assert.equal(Prel.assets('piuA').version, '1.2.3');
  assert.deepEqual(Prel.assets('piuA').js, ['a.js']);
  assert.deepEqual(Prel.assets('piuB').js, ['b1.js', 'b2.js']);
  assert.deepEqual(Prel.assets('piuB').css, []); // 未声明 css 默认空数组
  // 无参返回全部
  assert.deepEqual(Object.keys(Prel.assets()).sort(), ['piuA', 'piuB']);
});

test('autoLoad 返回声明的资源（仅 css，不触发 import）', async () => {
  Prel.define({ piuA: { version: '1.0.0', css: ['a.css'] } });
  const loaded = await Prel.autoLoad('piuA');
  assert.equal(loaded.length, 1);
  assert.deepEqual(loaded[0], { name: 'piuA', js: [], css: ['a.css'] });
});

test('autoLoad 未声明的 piu 给出警告且跳过', async () => {
  const loaded = await Prel.autoLoad('notExist');
  assert.deepEqual(loaded, []);
});

// ---------------- autoLoad 真正执行 js 入口（端到端） ----------------
test('autoLoad import .mjs 入口并触发其中的 Prel.start', async () => {
  delete globalThis.__helloPiuRendered;
  Prel.define({ helloPiu: { version: '1.0.0', js: ['./__fixtures__/hello-piu.mjs'] } });

  await Prel.autoLoad('helloPiu', { baseUrl: import.meta.url });

  // 入口已执行：piu 被声明，但依赖 locale 未就绪，回调还不该跑
  assert.ok(Prel.__getPiu('helloPiu'), 'piu 应已声明');
  assert.equal(globalThis.__helloPiuRendered, undefined, '依赖未就绪不应渲染');

  // 依赖就绪 -> 触发 start 回调
  const host = Prel.start('host', '1.0.0', [], () => {});
  host.setup({ locale: { value: 'zh', publicWritable: true } });

  assert.deepEqual(globalThis.__helloPiuRendered, { locale: 'zh', version: '1.0.0' });
  delete globalThis.__helloPiuRendered;
});

test('autoLoad fresh:true 可在同进程内重复执行同一入口', async () => {
  globalThis.__helloPiuLoadCount = 0;
  Prel.define({ counterPiu: { version: '1.0.0', js: ['./__fixtures__/counter-piu.mjs'] } });

  await Prel.autoLoad('counterPiu', { baseUrl: import.meta.url, fresh: true });
  await Prel.autoLoad('counterPiu', { baseUrl: import.meta.url, fresh: true });

  // fresh 绕过缓存，入口执行两次
  assert.equal(globalThis.__helloPiuLoadCount, 2);
  delete globalThis.__helloPiuLoadCount;
});

// ---------------- start / 依赖状态就绪 ----------------
test('start 无依赖时立即执行回调', () => {
  let called = false;
  Prel.start('p', '1.0.0', [], (piu, state) => {
    called = true;
    assert.equal(piu.name, 'p');
    assert.deepEqual(state, {});
  });
  assert.equal(called, true);
});

test('start 依赖未就绪时不执行，全部 setup 后执行', () => {
  let started = false;
  let received = null;
  Prel.start('waiter', '1.0.0', ['session', 'locale'], (piu, state) => {
    started = true;
    received = state;
  });
  assert.equal(started, false, '依赖未就绪不应执行');

  const host = Prel.start('host', '1.0.0', [], () => {});
  host.setup({ session: { value: 's1', publicWritable: true } });
  assert.equal(started, false, '只就绪一个依赖仍不应执行');

  host.setup({ locale: { value: 'en', publicWritable: true } });
  assert.equal(started, true, '全部依赖就绪应执行');
  assert.deepEqual(received, { session: 's1', locale: 'en' });
});

test('start 单个依赖（非数组）也支持', () => {
  let started = false;
  Prel.start('w', '1.0.0', 'session', () => { started = true; });
  assert.equal(started, false);
  const h = Prel.start('h', '1.0.0', [], () => {});
  h.setup({ session: { value: 1 } });
  assert.equal(started, true);
});

test('依赖已就绪时 start 立即执行', () => {
  const h = Prel.start('h', '1.0.0', [], () => {});
  h.setup({ session: { value: 1 } });
  let started = false;
  Prel.start('w', '1.0.0', ['session'], () => { started = true; });
  assert.equal(started, true);
});

// ---------------- 事件通信 ----------------
test('emit 触发普通事件', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  let got = null;
  piu.attach(piu, { hello: (a, b) => { got = [a, b]; } });
  piu.emit('hello', 1, 2);
  assert.deepEqual(got, [1, 2]);
});

test('emit 触发事件模块下的事件', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  let got = null;
  piu.attach(piu, { modA: { ev: (x) => { got = x; } } });
  piu.emit('modA', 'ev', 42);
  assert.equal(got, 42);
});

test('detach 解绑后不再触发', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  const obj = {};
  let count = 0;
  piu.attach(obj, { ev: () => { count++; } });
  piu.emit('ev');
  piu.detach(obj);
  piu.emit('ev');
  assert.equal(count, 1);
});

test('跨 piu 的事件能被另一个 piu 监听（emit 全局派发）', () => {
  const a = Prel.start('a', '1.0.0', [], () => {});
  const b = Prel.start('b', '1.0.0', [], () => {});
  let got = null;
  a.attach(a, { ping: (x) => { got = x; } });
  b.emit('ping', 42); // 由另一个 piu 触发
  assert.equal(got, 42);
});

test('跨 piu 的事件模块也能被另一个 piu 监听', () => {
  const a = Prel.start('a', '1.0.0', [], () => {});
  const b = Prel.start('b', '1.0.0', [], () => {});
  let got = null;
  a.attach(a, { modA: { ev: (x) => { got = x; } } });
  b.emit('modA', 'ev', 7);
  assert.equal(got, 7);
});

// ---------------- attach 的怪异覆盖行为 ----------------
test('怪异行为：同一绑定对象二次 attach 覆盖上次全部绑定', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  const log = [];
  piu.attach(piu, { testEvent1: () => log.push(1) });
  piu.attach(piu, { testEvent2: () => log.push(2) }); // 同对象 -> 覆盖
  piu.emit('testEvent1'); // 不触发
  piu.emit('testEvent2'); // 触发
  assert.deepEqual(log, [2]);
});

test('怪异行为：不同绑定对象互不覆盖', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  const log = [];
  piu.attach({}, { testEvent1: () => log.push(1) });
  piu.attach({}, { testEvent2: () => log.push(2) });
  piu.emit('testEvent1');
  piu.emit('testEvent2');
  assert.deepEqual(log, [1, 2]);
});

// ---------------- 状态通信 ----------------
test('setup / get / set', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  piu.setup({ locale: { value: 'en', publicWritable: true } });
  assert.equal(piu.get('locale'), 'en');
  piu.set('locale', 'zh');
  assert.equal(piu.get('locale'), 'zh');
});

test('get 未 setup 的状态返回 undefined', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  assert.equal(piu.get('nope'), undefined);
});

test('set 未 setup 的状态抛错', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  assert.throws(() => piu.set('nope', 1), /未 setup/);
});

test('publicWritable=false：其他 piu 不可写，owner 可写', () => {
  const owner = Prel.start('owner', '1.0.0', [], () => {});
  owner.setup({ secret: { value: 1, publicWritable: false } });
  const other = Prel.start('other', '1.0.0', [], () => {});
  assert.throws(() => other.set('secret', 2), /不允许其他 piu 写入/);
  // owner 自己可写
  owner.set('secret', 9);
  assert.equal(owner.get('secret'), 9);
});

test('publicWritable=true：其他 piu 可写', () => {
  const owner = Prel.start('owner', '1.0.0', [], () => {});
  owner.setup({ shared: { value: 1, publicWritable: true } });
  const other = Prel.start('other', '1.0.0', [], () => {});
  other.set('shared', 2);
  assert.equal(owner.get('shared'), 2);
});

// ---------------- $stateChange 监听 ----------------
test('$stateChange 在 set 时收到 (newValue, oldValue)', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  piu.setup({ locale: { value: 'en', publicWritable: true } });
  const changes = [];
  piu.attach(piu, { $stateChange: { locale: (n, o) => changes.push([o, n]) } });
  piu.set('locale', 'zh');
  piu.set('locale', 'fr');
  assert.deepEqual(changes, [['en', 'zh'], ['zh', 'fr']]);
});

test('set 相同值不触发 $stateChange', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  piu.setup({ locale: { value: 'en', publicWritable: true } });
  let count = 0;
  piu.attach(piu, { $stateChange: { locale: () => count++ } });
  piu.set('locale', 'en'); // 相同值
  assert.equal(count, 0);
});

test('跨 piu 的状态变更能被另一个 piu 监听', () => {
  const a = Prel.start('a', '1.0.0', [], () => {});
  a.setup({ theme: { value: 'light', publicWritable: true } });
  const b = Prel.start('b', '1.0.0', [], () => {});
  let seen = null;
  b.attach(b, { $stateChange: { theme: (n) => { seen = n; } } });
  a.set('theme', 'dark');
  assert.equal(seen, 'dark');
});

// ---------------- ready ----------------
test('ready：状态已就绪立即 resolve', async () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  piu.setup({ locale: { value: 'zh', publicWritable: true } });
  const state = await piu.ready(['locale']);
  assert.equal(state.locale, 'zh');
});

test('ready：等待后续 setup 才 resolve', async () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  const promise = piu.ready(['session']).then((s) => s.session);
  piu.setup({ session: { value: 'sx', publicWritable: true } });
  assert.equal(await promise, 'sx');
});

test('ready：多个状态全部就绪才 resolve', async () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  let resolved = false;
  const promise = piu.ready(['a', 'b']).then((s) => { resolved = true; return s; });
  piu.setup({ a: { value: 1 } });
  await Promise.resolve(); // 让微任务跑一轮
  assert.equal(resolved, false);
  piu.setup({ b: { value: 2 } });
  const state = await promise;
  assert.equal(resolved, true);
  assert.deepEqual({ a: state.a, b: state.b }, { a: 1, b: 2 });
});

// ---------------- config ----------------
test('config 写入与读取', () => {
  Prel.config({ env: 'test', debug: true });
  Prel.config({ debug: false }); // 合并
  assert.deepEqual(Prel.config(), { env: 'test', debug: false });
});

// ---------------- 测试辅助 ----------------
test('__getPiu 取到已创建的 piu 实例', () => {
  const piu = Prel.start('p', '1.0.0', [], () => {});
  assert.equal(Prel.__getPiu('p'), piu);
});

test('__reset 清空所有内部状态', () => {
  Prel.define({ x: { version: '1.0.0' } });
  Prel.start('p', '1.0.0', [], () => {}).setup({ s: { value: 1 } });
  Prel.config({ a: 1 });
  Prel.__reset();
  assert.deepEqual(Prel.assets(), {});
  assert.equal(Prel.__getPiu('p'), undefined);
  assert.deepEqual(Prel.config(), {});
});

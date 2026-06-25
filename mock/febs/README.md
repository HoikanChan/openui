# Prel Mock

一个用于**测试**的最简但功能完整的 prelude (Prel) 实现，ESM 单文件、零运行时依赖。

prelude 是一套以 **piu（页面集成单元）** 为粒度的微前端框架：每个 piu 声明自己的 js/css 资源，被加载执行后通过 `Prel.start` 注册，并依赖一组**全局状态**就绪后才渲染；piu 之间通过**事件**和**状态**通信。本 mock 还原了这套机制（包括官方文档没说清的几个怪异行为），方便你在 Node 下写单元/集成测试。

> ⚠️ 这是**测试替身**，不是真实 prelude。行为对齐文档描述，但实现细节（资源加载、内部数据结构）是为可测试性服务的简化版。差异见文末「与真实 prelude 的差异」。

---

## 文件

| 文件 | 说明 |
| --- | --- |
| `prel-mock.mjs` | mock 主体，默认导出 + 具名导出 `Prel`（同一单例） |
| `prel-mock.test.mjs` | 28 个用例，覆盖全部 API 与怪异行为 |
| `__fixtures__/hello-piu.mjs` | 示范 piu 入口：被 `autoLoad` 执行时调用 `Prel.start` |
| `__fixtures__/counter-piu.mjs` | 验证 `autoLoad({ fresh: true })` 重复加载的计数 fixture |

## 运行测试

```bash
node --test prel-mock.test.mjs
```

需要 Node 18+（用到内置 `node:test` 与动态 `import()`）。

## 导入

```js
import Prel from './prel-mock.mjs';      // 默认导出
import { Prel } from './prel-mock.mjs';   // 具名导出，指向同一对象
```

两种导出指向同一单例，内部状态共享。浏览器环境下还会挂 `window.Prel`，便于被 `autoLoad` 下来的脚本通过全局访问。

---

## 快速上手

```js
import Prel from './prel-mock.mjs';

// 1. 声明资源
Prel.define({
  helloPiu: { version: '1.0.0', js: ['./piu/hello.mjs'], css: ['./piu/hello.css'] },
});

// 2. 加载并执行入口（入口里会调用 Prel.start）
await Prel.autoLoad('helloPiu', { baseUrl: import.meta.url });

// 3. 由某个 piu 初始化依赖状态，触发各 piu 的渲染回调
const host = Prel.start('host', '1.0.0', [], () => {});
host.setup({ locale: { value: 'zh', publicWritable: true } });
```

`./piu/hello.mjs` 入口长这样：

```js
import Prel from '../prel-mock.mjs';

Prel.start('helloPiu', '1.0.0', ['locale'], (socket, state) => {
  // 依赖的状态全部就绪后才执行；用任意技术栈渲染 UI
  // socket：与其它 piu 通信的实例（即文档里第一个回调参数 piu）
  // state ：当前全部状态快照（只读）
  render(state.locale);
});
```

---

## API

### 顶层 `Prel`

#### `Prel.define(defs) → Prel`

声明所有 piu 的名字与资源。

```js
Prel.define({
  piuA: { version: '1.2.3', js: ['a.js'], css: ['a.css'] },
  piuB: { version: '2.0.0', js: ['b1.js', 'b2.js'] }, // css 可省略，默认 []
});
```

#### `Prel.assets([piuName]) → object`

查询已声明的资源。传名字返回单个 `{ name, version, js, css }`；不传返回全部的字典。

#### `Prel.autoLoad(piuNames, opts?) → Promise<Array>`

根据 piu 名找到声明的 js/css 并**真正加载执行**：

- **Node**：把每个 `js` 当作可执行入口 `import()`，入口里的 `Prel.start` 会真实生效，完成 piu 声明闭环。css 在 Node 下无意义，跳过。
- **浏览器**：插入 `<link>` / `<script>` 标签，由浏览器执行。

`piuNames` 可传单个字符串或数组。返回 Promise，resolve 后所有入口已执行完毕，值为 `[{ name, js, css }, ...]`。

`opts`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `baseUrl` | string | Node 下解析**相对** js 路径的基准，通常传 `import.meta.url`。绝对路径 / 包名不需要。 |
| `fresh` | boolean | 为 `true` 时给 import 路径追加唯一查询串，绕过 ESM 模块缓存，强制重新执行入口（同进程内重复加载同一 piu 时用）。 |

```js
await Prel.autoLoad('helloPiu', { baseUrl: import.meta.url });
await Prel.autoLoad(['piuA', 'piuB'], { baseUrl: import.meta.url, fresh: true });
```

> **ESM 缓存提示**：模块默认只 evaluate 一次。不加 `fresh`，同进程内对同一文件再次 `autoLoad` 不会重复执行入口里的 `Prel.start`。

#### `Prel.start(name, version, deps, cb) → piu`

声明一个 piu。`deps` 是依赖的状态名数组（也接受单个字符串）；**只有依赖的全部状态被 `setup` 后，`cb` 才执行**。无依赖则立即执行。返回 piu 实例。

```js
Prel.start('testPiu', '1.0.0', ['session', 'locale'], (socket, state) => {
  // socket 即文档里的第一个回调参数（文档命名为 piu，实为通信 socket）
  // state 是全部状态的只读快照
});
```

#### `Prel.config(obj?) → Prel | object`

写入（合并）或读取全局配置。传对象写入，不传读取当前全部。

```js
Prel.config({ env: 'test' });
Prel.config({ debug: true }); // 合并
Prel.config(); // → { env: 'test', debug: true }
```

### piu 实例

`Prel.start(...)` 返回，或 `Prel.__getPiu(name)` 取得。

#### 事件通信

```js
// 监听：第一个参数是绑定对象（也是事件回调的 this 语义来源）
piu.attach(thisObj, {
  eventName: (...args) => {},
  moduleName: { eventName: (...args) => {} }, // 事件模块
});

// 触发
piu.emit('eventName', a, b);
piu.emit('moduleName', 'eventName', a, b);

// 解绑：按绑定对象整体解绑
piu.detach(thisObj);
```

> ⚠️ **怪异行为（已还原）**：以「绑定对象」为 key 存储绑定，**用同一个对象第二次 `attach`，会覆盖上一次该对象的全部绑定**；用不同对象则互不影响。`detach` 也是按对象整体解绑。

```js
piu.attach(piu, { e1: () => log.push(1) });
piu.attach(piu, { e2: () => log.push(2) }); // 同对象 → 覆盖
piu.emit('e1'); // 不触发
piu.emit('e2'); // 触发

piu.attach({}, { e1: () => log.push(1) });
piu.attach({}, { e2: () => log.push(2) }); // 不同对象 → 都保留
```

`emit` 判定规则：**第二个参数是字符串**时按 `emit(module, event, ...params)` 解析，否则按 `emit(event, ...params)`。如果你的事件首个参数本身就是字符串，注意这个歧义。

#### 状态通信

```js
// 初始化状态（setup 后才可 set）
piu.setup({
  locale: { value: 'en', publicWritable: true }, // publicWritable=false 时仅 owner 可写
});

piu.get('locale');           // 读
piu.set('locale', 'zh');     // 写

// 监听状态变更（挂在 attach 的 $stateChange 下）
piu.attach(piu, {
  $stateChange: {
    locale: (newValue, oldValue) => {},
  },
});

// 等待状态就绪（同 start 的第三个参数语义），resolve 出全部状态快照
const state = await piu.ready(['locale', 'session']);
```

状态规则：

- `set` 未经 `setup` 的状态 → 抛错。
- `publicWritable: false` 时，**只有声明该状态的 owner piu 能 `set`**，其它 piu `set` 抛错。
- `set` 相同值不触发 `$stateChange`。
- 状态变更是**全局**的：任何 piu 都能监听到别的 piu 改的状态。

### 测试辅助（非真实 prelude API）

| 方法 | 说明 |
| --- | --- |
| `Prel.__reset()` | 清空所有内部状态（registry / config / piu / 状态表 / 等待队列）。**注意**：清不掉 ESM 模块缓存，重复加载同一 fixture 仍需 `fresh`。 |
| `Prel.__getPiu(name)` | 取已创建的 piu 实例。 |

测试里建议在 `beforeEach` 调 `__reset` 隔离用例：

```js
import { beforeEach } from 'node:test';
beforeEach(() => Prel.__reset());
```

---

## 与真实 prelude 的差异

- `autoLoad` 在 Node 下用 `import()` 执行 `.mjs` 入口（真实环境是浏览器插 `<script>`）；不处理打包、依赖图、加载顺序、css 真实生效。
- `autoLoad` 返回 **Promise**（真实 API 行为可能不同），记得 `await`。
- `emit` 的模块/普通事件判定基于「第二参数是否为字符串」，是简化推断，非真实协议解析。
- piu 入口通过 `import Prel from '...'` 拿到单例，等价于真实环境访问全局 `Prel`。
- 不实现真实 prelude 的版本协商、沙箱隔离、错误上报等。

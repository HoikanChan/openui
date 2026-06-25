/**
 * Prel Mock (ESM) —— 用于测试的最简但功能完整的 prelude 实现
 *
 * 覆盖能力：
 *   - Prel.define / Prel.assets / Prel.autoLoad / Prel.start / Prel.config
 *   - piu 实例：attach / detach / emit
 *   - 状态：setup / get / set / ready / $stateChange
 *   - 依赖状态就绪后才触发 start 回调
 *   - 还原 attach 的「同一绑定对象二次 attach 覆盖上次绑定」的怪异行为
 *
 * 用法：
 *   import Prel from './prel-mock.mjs'
 *   import { Prel } from './prel-mock.mjs'   // 具名导出同一个对象
 */

// ============ 全局共享数据 ============
const registry = {};          // piuName -> { name, version, js, css }
const config = {};            // Prel.config 写入的全局配置
const startedPius = {};       // piuName -> piu 实例

// 全局状态表： stateName -> { value, publicWritable, isSetup, owner }
const stateStore = {};
// 状态就绪的等待队列： stateName -> [resolve, ...]
const stateReadyWaiters = {};

function emitGlobalStateChange(name, newValue, oldValue) {
  Object.values(startedPius).forEach((piu) => piu._fireStateChange(name, newValue, oldValue));
}

function resolveStateReady(name) {
  (stateReadyWaiters[name] || []).forEach((fn) => fn());
  stateReadyWaiters[name] = [];
}

// ============ Piu 实例 ============
function createPiu(name, version) {
  // 以「绑定对象」为 key 记录绑定，还原同一对象二次 attach 覆盖的怪异行为
  const bindings = new Map(); // thisObj -> { events: {...}, stateChange: {...} }

  const piu = {
    name,
    version,

    // ---- 事件 / 状态监听绑定 ----
    attach(thisObj, handlers) {
      const events = {};
      const stateChange = {};

      for (const key of Object.keys(handlers || {})) {
        const val = handlers[key];
        if (key === '$stateChange' && val && typeof val === 'object') {
          // 状态变更监听： { locale: (n,o)=>{} }
          Object.assign(stateChange, val);
        } else if (typeof val === 'function') {
          // 普通事件： eventName: fn
          events[key] = val;
        } else if (val && typeof val === 'object') {
          // 事件模块： eventModuleName: { eventName: fn }
          events[key] = val;
        }
      }

      // 覆盖式写入（怪异行为的关键）：同一 thisObj 直接替换上次绑定
      bindings.set(thisObj, { events, stateChange });
      return piu;
    },

    detach(thisObj) {
      bindings.delete(thisObj);
      return piu;
    },

    emit(...args) {
      let moduleName = null;
      let eventName;
      let params;

      // emit(event, ...p) 还是 emit(module, event, ...p)
      if (typeof args[1] === 'string') {
        [moduleName, eventName, ...params] = args;
      } else {
        [eventName, ...params] = args;
      }

      for (const { events } of bindings.values()) {
        if (moduleName) {
          const mod = events[moduleName];
          if (mod && typeof mod[eventName] === 'function') mod[eventName](...params);
        } else if (typeof events[eventName] === 'function') {
          events[eventName](...params);
        }
      }
      return piu;
    },

    // ---- 状态 API ----
    setup(stateMap) {
      for (const sName of Object.keys(stateMap || {})) {
        const { value, publicWritable = false } = stateMap[sName];
        const prev = stateStore[sName];
        stateStore[sName] = { value, publicWritable, isSetup: true, owner: name };
        resolveStateReady(sName);
        checkPendingStarts();
        emitGlobalStateChange(sName, value, prev ? prev.value : undefined);
      }
      return piu;
    },

    get(sName) {
      const s = stateStore[sName];
      return s ? s.value : undefined;
    },

    set(sName, value) {
      const s = stateStore[sName];
      if (!s || !s.isSetup) throw new Error(`[Prel] state "${sName}" 未 setup，不能 set`);
      if (!s.publicWritable && s.owner !== name) {
        throw new Error(`[Prel] state "${sName}" 不允许其他 piu 写入`);
      }
      const oldValue = s.value;
      s.value = value;
      if (oldValue !== value) emitGlobalStateChange(sName, value, oldValue);
      return piu;
    },

    ready(names) {
      const list = Array.isArray(names) ? names : [names];
      return Promise.all(
        list.map(
          (n) =>
            new Promise((resolve) => {
              if (stateStore[n] && stateStore[n].isSetup) resolve();
              else (stateReadyWaiters[n] = stateReadyWaiters[n] || []).push(resolve);
            })
        )
      ).then(() => snapshotState());
    },

    // ---- 内部：分发状态变更 ----
    _fireStateChange(sName, newValue, oldValue) {
      for (const { stateChange } of bindings.values()) {
        if (typeof stateChange[sName] === 'function') stateChange[sName](newValue, oldValue);
      }
    },
  };

  return piu;
}

function snapshotState() {
  const out = {};
  for (const k of Object.keys(stateStore)) out[k] = stateStore[k].value;
  return out;
}

// ============ start 的依赖等待队列 ============
const pendingStarts = []; // { name, version, deps, cb, piu }

function depsReady(deps) {
  return deps.every((d) => stateStore[d] && stateStore[d].isSetup);
}

function checkPendingStarts() {
  for (let i = pendingStarts.length - 1; i >= 0; i--) {
    const p = pendingStarts[i];
    if (depsReady(p.deps)) {
      pendingStarts.splice(i, 1);
      p.cb(p.piu, snapshotState());
    }
  }
}

// ============ Prel 顶层 API ============
const Prel = {
  /**
   * 声明所有 piu 的名字与资源
   * @param {Object} defs  { piuName: { version, js:[], css:[] }, ... }
   */
  define(defs) {
    for (const piuName of Object.keys(defs || {})) {
      const d = defs[piuName] || {};
      registry[piuName] = {
        name: piuName,
        version: d.version || '0.0.0',
        js: [].concat(d.js || []),
        css: [].concat(d.css || []),
      };
    }
    return Prel;
  },

  /** 查看所有已声明 piu 的资源信息 */
  assets(piuName) {
    if (piuName) return registry[piuName];
    return { ...registry };
  },

  /**
   * 根据 piu 名加载其声明的 js / css 资源，并真正执行 js。
   *   - 浏览器环境：插入 link / script 标签（script 由浏览器自行执行）
   *   - Node 环境：把每个 js 当作可执行入口 import()，入口里调用的
   *     Prel.start 等会真正生效，完成 piu 声明闭环
   * 返回 Promise，resolve 后所有入口已执行完毕。
   * @param {string|string[]} piuNames
   * @param {{ baseUrl?: string, fresh?: boolean }} [opts]
   *   baseUrl: Node 下解析相对 js 路径的基准（如 import.meta.url）
   *   fresh:   为 true 时给 import 路径追加唯一查询串，绕过 ESM 模块缓存，
   *            强制重新执行入口（便于在同一进程内多次加载同一 piu）
   */
  async autoLoad(piuNames, opts = {}) {
    const list = [].concat(piuNames || []);
    const loaded = [];

    for (const piuName of list) {
      const def = registry[piuName];
      if (!def) {
        console.warn(`[Prel] autoLoad: 未声明的 piu "${piuName}"`);
        continue;
      }

      if (typeof document !== 'undefined') {
        // ---- 浏览器：插标签，script 由浏览器执行 ----
        def.css.forEach((href) => {
          const link = document.createElement('link');
          link.rel = 'stylesheet';
          link.href = href;
          document.head.appendChild(link);
        });
        def.js.forEach((src) => {
          const script = document.createElement('script');
          script.src = src;
          document.head.appendChild(script);
        });
      } else {
        // ---- Node：真正 import 每个 js 入口，触发其中的 Prel.start ----
        for (const src of def.js) {
          let target = src;
          // 相对路径基于 opts.baseUrl 解析；否则交给默认解析（绝对路径 / 包名）
          if (opts.baseUrl && /^[./]/.test(src)) {
            target = new URL(src, opts.baseUrl).href;
          }
          // fresh：追加唯一查询串绕过模块缓存
          if (opts.fresh) {
            target += (target.includes('?') ? '&' : '?') + 'prel_t=' + Date.now() + '_' + Math.random().toString(36).slice(2);
          }
          try {
            await import(target);
          } catch (e) {
            console.warn(`[Prel] autoLoad: 执行 "${src}" 失败 -> ${e.message}`);
          }
        }
        // css 在 Node 下无意义，跳过
      }

      loaded.push({ name: piuName, js: def.js, css: def.css });
    }

    return loaded;
  },

  /**
   * 声明一个 piu
   * @param {string} name
   * @param {string} version
   * @param {string[]} deps  依赖的状态名，全部 setup 后才执行回调
   * @param {(piu, state)=>void} cb
   */
  start(name, version, deps, cb) {
    const piu = startedPius[name] || createPiu(name, version);
    startedPius[name] = piu;

    const depList = Array.isArray(deps) ? deps : deps ? [deps] : [];

    if (depList.length === 0 || depsReady(depList)) {
      cb(piu, snapshotState());
    } else {
      pendingStarts.push({ name, version, deps: depList, cb, piu });
    }
    return piu;
  },

  /** 写入 / 读取全局配置 */
  config(obj) {
    if (obj === undefined) return { ...config };
    Object.assign(config, obj);
    return Prel;
  },

  // ---- 测试辅助：重置所有内部状态 ----
  __reset() {
    for (const k of Object.keys(registry)) delete registry[k];
    for (const k of Object.keys(config)) delete config[k];
    for (const k of Object.keys(startedPius)) delete startedPius[k];
    for (const k of Object.keys(stateStore)) delete stateStore[k];
    for (const k of Object.keys(stateReadyWaiters)) delete stateReadyWaiters[k];
    pendingStarts.length = 0;
    return Prel;
  },

  // ---- 测试辅助：拿到 piu 实例 ----
  __getPiu(name) {
    return startedPius[name];
  },
};

// 浏览器全局挂载（可选，方便被 autoLoad 下来的脚本访问 window.Prel）
if (typeof window !== 'undefined') window.Prel = Prel;

export default Prel;
export { Prel };

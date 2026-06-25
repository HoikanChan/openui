// 一个真实的 piu 入口：被 autoLoad import 时执行，调用 Prel.start 声明自己。
// 注意：ESM 模块只会被 evaluate 一次。同一进程内对本文件重复 autoLoad 不会再次
// 执行这里的 Prel.start（模块缓存所致）。若测试需要“重新加载”，请换不同 fixture
// 文件，或给 import 路径加 ?t=<唯一值> 查询串来绕过缓存。
import Prel from '../prel-mock.mjs';

Prel.start('helloPiu', '1.0.0', ['locale'], (socket, state) => {
  // 渲染逻辑（这里用全局标记代替真实 UI 渲染）
  globalThis.__helloPiuRendered = { locale: state.locale, version: socket.version };
});

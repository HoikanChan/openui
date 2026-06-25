// 每次被执行时给全局计数 +1，用于验证 autoLoad fresh:true 的重复加载
globalThis.__helloPiuLoadCount = (globalThis.__helloPiuLoadCount || 0) + 1;

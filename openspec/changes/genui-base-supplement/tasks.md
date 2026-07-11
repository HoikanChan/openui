# GenUI Base Supplement — 任务清单

## 1. 数据模型与 loader

- [x] 1.1 新增 `GenUIBaseSupplement` record（components / componentGroups / examples / additionalRules，紧凑构造器防御性拷贝，Javadoc 声明 loader 为唯一受支持构造路径）
- [x] 1.2 将 `GenerationContractLoader` 的 `componentMap` / `componentGroups` / `strings` 私有解析方法提升为包内可见，供增补包 loader 复用
- [x] 1.3 新增 `GenUIBaseSupplementLoader.fromJson(String)`：顶层键白名单校验（非法键抛 `GenerationSdkException` 并列出全部非法键）+ 复用 1.2 的解析
- [x] 1.4 新增 `GenUIBaseSupplementLoader.fromResource(String)`：classpath 读取后委托 `fromJson`，资源缺失抛 `GenerationSdkException`
- [x] 1.5 loader 单测：合法解析（两种组件形态、缺省 section 为空集合）、非法顶层键（含 tools/root/builtins 及拼错键名）、fromResource 缺失资源

## 2. 构建期合并与校验

- [x] 2.1 在 `GenUIBaseSupplement` 实现包私有 `applyTo(GenerationContract)`：components putAll 替换/追加、同名 group components 保序去重并集 + notes 追加、examples/additionalRules 追加、其余 section 原样保留
- [x] 2.2 `GenerationSdk.Builder` 新增 `baseSupplement(...)`；私有构造器在现有校验前：先以 scope "base supplement" 对增补包组件跑 `validateComponents`，再合并并将结果存入现有 `baseContract` 字段
- [x] 2.3 合并语义单测：新组件追加顺序、同名组件整体替换且保持原位置、group 并集（成员顺序/去重/notes）、不可变 section 保持 base 原值
- [x] 2.4 校验单测：增补包 propsSchema 非法（消息含 "base supplement" 与组件名）、增补包 group 引用缺失组件、`register` 与增补包组件同名碰撞

## 3. 端到端与回归

- [x] 3.1 含增补包的 `assemblePrompt` 端到端单测：新组件 spec 出现在 prompt、替换组件的文档来自增补包、增补 examples/rules 出现在对应 section
- [x] 3.2 运行 `mvn test` 确认 `PromptGoldenTest` 等既有用例全部通过（无增补包路径字节级不变）

## 4. 文档

- [x] 4.1 README 新增「Base Supplement」章节：JSON 格式（含合并规则表：组件=整体替换、group=并集、examples/rules=追加）、使用示例、宿主责任提示（组件须已在宿主前端注册；SDK 升级时 review 同名替换条目）
- [x] 4.2 撰写 ADR `packages/genui-java-sdk/docs/adr/0002-genui-base-supplement.md`：记录整体替换 vs merge-patch、单份 JSON vs 程序化 API、不提供前端导出工具等取舍

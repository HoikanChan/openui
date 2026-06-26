## 1. worker 实现 `--extension` 分支

- [x] 1.1 在 [generate-worker.ts](packages/openui-cli/src/commands/generate-worker.ts) 解析 `--extension`、`--extension-id <id>`、`--version <ver>` 参数（与现有 `--export` 解析共存）
- [x] 1.2 新增 extension 对象识别：在打包后的模块导出里查找含数组型 `components`（元素具备 `name` + `props`）的对象，支持 `--export <name>` 显式选择；未命中或多候选时报错并列出可用导出名、提示用 `--export`
- [x] 1.3 通过 esbuild stub 取得 `createLibrary`：把用户入口与 `export { createLibrary } from "@cloudsop/openui-react-lang"` 一起打包，使组件与 `createLibrary` 共享同一个 bundled zod 实例（避免跨实例 zod introspection 出错）；解析失败时报清晰错误，提示在含 OpenUI 依赖的项目内运行
- [x] 1.4 编译并组装输出：对 extension 对象执行 `createLibrary({ components, componentGroups, tools, examples, additionalRules }).toSpec()` 得到编译后的 `components` 与透传字段；`extensionId = flag ?? 对象`、`version = flag ?? 对象 ?? ""`；`componentGroups`/`tools`/`examples`/`additionalRules` 仅在非空时输出；`extensionId` 两处都缺失时报错退出
- [x] 1.5 `createLibrary` 抛出的「必填先于可选」校验错误原样透传，使命令以非零状态码失败并指明组件与属性

## 2. CLI 命令接线（generate-extension）

- [x] 2.1 将 [generate-component-spec.ts](packages/openui-cli/src/commands/generate-component-spec.ts) 改名为 `generate-extension.ts`：函数/类型改名，flag 由 `--generation-id` 改为 `--extension-id`，新增 `--version`，向 worker 传 `--extension`/`--extension-id`/`--version`/`--export`
- [x] 2.2 在 [index.ts](packages/openui-cli/src/index.ts) 注册 `generate-extension` 命令，定义 `--extension-id`、`--version`、`--export`、`-o/--out`、`--no-interactive` 选项
- [x] 2.3 `-o/--out` 写文件（创建缺失父目录），否则写 stdout

## 3. 移除公开导出

- [x] 3.1 从 [react-lang/index.ts](packages/react-lang/src/index.ts) 和 [lang-core/index.ts](packages/lang-core/src/index.ts) 的 barrel 移除 `generateComponentSpecs` 导出（保留 lang-core 内部实现）
- [x] 3.2 确认 [library-extension.test.ts](packages/lang-core/src/__tests__/library-extension.test.ts) 走相对路径 `from "../library"` 仍可编译运行

## 4. 文档更新

- [x] 4.1 改写 [extending-openui.mdx](docs/content/docs/openui-lang/extending-openui.mdx) 「从前端库导出组件 spec」小节：脚本写法 → CLI `generate-extension` 写法（入口导出完整 extension 对象）
- [x] 4.2 全文检索 [docs/SYSTEM-DESIGN.md](docs/SYSTEM-DESIGN.md) 中 `generateComponentSpecs`，逐处把「DSLEngine 提供 generateComponentSpecs」改为「CLI 生成 Extension JSON」

## 5. 验证

- [x] 5.1 用示例 extension 对象运行 CLI，确认输出 JSON 与旧 `generateComponentSpecs(...)` 脚本结果一致（含字段顺序）
- [x] 5.2 验证 flag 覆盖对象、`version` 两处缺失输出 `""`、`extensionId` 两处缺失报错、无扩展字段时不输出空键
- [x] 5.3 运行 lang-core 单测与 CLI 构建（`build:cli`），确认移除导出后无类型/构建错误

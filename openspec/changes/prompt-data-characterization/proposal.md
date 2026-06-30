## Why

GenUI Java SDK 当前把整份 host data 原样 pretty-print 进 system prompt(见 `PromptAssembler.dataModelSection`):一张 1 万行的表格或一条上万点的图表序列会瞬间撑爆 token 预算,甚至超出上下文窗口,而模型其实并不需要这些数据本身——真正用于渲染的全量数据是通过 seq=0 的 `dataModel` envelope 单独下发给运行时的,prompt 里的副本只是给模型"看懂数据形状"用的。因此我们可以、也应该在不损失渲染效果的前提下,对 prompt 副本做激进的结构化采样。

## What Changes

- 新增 **Characterization**:一个组装前的 `DataModelSpec → DataModelSpec` 纯函数变换,把"全量 prompt 副本"换成"采样树 + TS-type sidecar"。
- `raw` 被替换为**同构采样树**:大数组截断到 K 行样本、长字符串截断,字段结构(键名、嵌套形状)保持不变,从而 `data.<field>` 引用路径全部仍然有效。
- `description` 被富化为原描述 + 自动生成的 **TS-type sidecar**:承载完整 schema、**完整枚举域(union 字面量)**、数组/对象 count。枚举域以 `"open"|"closed"|"pending"` 形式给出,与模型要生成的 `@Switch` case 同构,杜绝漏 case。
- 仅在 `raw` 序列化体积超过阈值(默认 ~2KB)时触发;小数据原样通过,行为与 golden 不变。
- **不改** `PromptAssembler` / `prompt.ts` / golden fixture:Characterization 输出仍是标准 `DataModelSpec`,走现有组装路径,跨语言字节对齐零风险。
- **效果验证**是本变更的一等交付物:提供可度量的 token 压缩比 + 形状保真度(schema/枚举/count 无损)双重验证,而非仅单测通过。

## Capabilities

### New Capabilities
- `prompt-data-characterization`: 在 prompt 组装前对 host data 做确定性、无 LLM 的结构化采样与特征刻画,在保持 `data.<field>` 引用可用与枚举域完整的前提下,把超大 data model 压缩进 token 预算,并要求对压缩比与形状保真度做完整效果验证。

### Modified Capabilities
<!-- 无:Characterization 产出标准 DataModelSpec,assembler 的 spec 级行为不变。 -->

## Impact

- **新增代码**:`packages/genui-java-sdk` 内新增 Characterization 模块(walker + TS-type 渲染 + 枚举/计数扫描)及其单测;无新增第三方依赖。
- **接入点**:`GenUiGenerator.toPromptRequest`(构造 `DataModelSpec` 处)在喂给 assembler 前调用 Characterization;`GenerationSdk` 暴露开关与阈值配置。
- **不变量**:Render Data Model(seq=0 envelope)恒为全量,Characterization 只动 prompt 副本。
- **不受影响**:`PromptAssembler`、`prompt.ts`、`prompt-golden` fixture 不改动。
- **风险**:仅 prompt 侧文本变化,不影响渲染结果;主要风险是采样导致模型选错组件或漏枚举——由效果验证环节量化兜底。

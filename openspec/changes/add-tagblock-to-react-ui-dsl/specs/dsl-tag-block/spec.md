## ADDED Requirements

### Requirement: TagBlock 组件存在且 schema 正确

DSL 库 SHALL 提供名为 `TagBlock` 的组件，其 schema 字段为：

1. `tags`: `string[]`（必填）

DSL 位置参数示例：`TagBlock(["Landmark", "City Break", "Culture"])` 表示渲染三个字符串标签。

#### Scenario: 组件注册后可在 DSL 中使用

- **WHEN** DSL 库初始化完成
- **THEN** `TagBlock` 组件 MUST 出现在库的组件列表中，且 schema 验证通过

#### Scenario: tags 为空数组时 schema 验证通过

- **WHEN** DSL 传入 `tags: []`
- **THEN** schema 验证 MUST 通过，渲染结果为空容器

#### Scenario: schema 拒绝非字符串数组

- **WHEN** DSL 传入 `tags: [1, 2, 3]`（数字数组）
- **THEN** schema 验证 MUST 返回错误

### Requirement: TagBlock 的 description 与参考库一致

`TagBlock` 组件的 `description` 字段 MUST 为：

```
tags is an array of strings
```

#### Scenario: LLM 看到的组件描述正确

- **WHEN** 调用 `dslLibrary.prompt()` 或读取组件 description
- **THEN** description MUST 为 `"tags is an array of strings"`

### Requirement: TagBlock 渲染所有 tags 为标签元素

`TagBlock` 组件 SHALL 将 `tags` 数组中的每个字符串渲染为一个标签元素，并包裹在 flex 容器中。

#### Scenario: 渲染多个标签

- **WHEN** `TagBlock` 的 `tags` 为 `["React", "TypeScript", "DSL"]`
- **THEN** 渲染结果 MUST 包含 3 个标签元素，文本分别为 `"React"`、`"TypeScript"`、`"DSL"`

#### Scenario: 容器为 flex 横向排列且允许换行

- **WHEN** `TagBlock` 渲染时
- **THEN** 容器样式 MUST 包含 `display: flex` 和 `flexWrap: wrap`

### Requirement: TagBlock 注册在 dslLibrary 中

`dslLibrary` MUST 在 `baseDslLibrary` 的 `components` 数组中包含 `TagBlock`。

#### Scenario: TagBlock 在 library 组件列表中

- **WHEN** 读取 `dslLibrary` 的组件注册列表
- **THEN** 列表 MUST 包含名为 `TagBlock` 的组件

# Prompt 模板使用说明

SDK 用 `PromptRenderer` 渲染 classpath 文本资源。repair 的两份完整模板在 `src/main/resources/prompts/repair/system.md` 和 `user.md`，从头到尾就是消息结构，没有局部 View 分区或 metadata。

这是零新增依赖的受限模板实现，不兼容完整 Handlebars 或 Dotprompt。支持变量、if/else 和 for。

## Java 入口

```java
String message = PromptRenderer.render("prompts/example.md", model);
```

资源路径不带开头的 `/`，数据为 `Map<String, ?>`。资源按 UTF-8 加载并缓存解析结果；每次使用当前 model 渲染，不缓存消息。下面的 example.md 为说明用路径，需自行创建资源。

## 变量与条件

```markdown
请修复当前程序。

{{#if program}}
## 当前程序
{{program}}
{{/if}}
```

直接插入 `{{program}}`，不做 HTML 转义，也不再次解析插入值。DSL 中的 `{{other}}` 会原样保留。变量只支持字母、数字、下划线，不能以数字开头；不支持点路径、方法调用或表达式。

`if` 直接判断对应值，无需重复传 hasXxx：

| 值 | 条件结果 |
| --- | --- |
| Boolean | 使用其布尔值 |
| CharSequence | 非空白为 true |
| Collection、Map | 非空为 true |
| null | false |
| 缺失变量 | 报错 |
| 数字或其他类型 | 报错 |

支持可选的 `else` 和嵌套条件。组合业务判断仍由 Java 计算，不支持 `&&`、`!`、比较等表达式。

```markdown
{{#if notice}}
{{notice}}
{{else}}
备用文案。
{{/if}}
```

直接插入的变量必须存在且非 null。未选中分支的变量无需提供，但整个模板的结构仍会被解析检查。

## For 循环

Java 传入由 Map 条目组成的 Collection，模板负责循环和完整条目文案：

```java
Map<String, Object> model = Map.of(
        "program", "root = Stack([])",
        "diagnostics", List.of(
                Map.of("code", "syntax-unclosed-bracket", "message", "括号未闭合", "hint", "补全括号"),
                Map.of("code", "unresolved-ref", "message", "引用不存在", "hint", "")));
String message = PromptRenderer.render("prompts/example.md", model);
```

```markdown
{{#if diagnostics}}
## 校验诊断
{{#for diagnostics}}
### {{code}}
{{message}}
{{#if hint}}
- 修复提示：{{hint}}
{{/if}}

{{/for}}
{{/if}}
```

每次循环先从当前条目的 Map 读取变量；当前条目没有该键时，再读取外层 model。当前条目显式设置为 null 时，不会回退到外层同名值。

循环允许 if/else，条件块也可以包住循环。不支持循环套循环。空集合或 null 不输出循环正文；缺失集合、集合类型错误、条目不是 Map 或键不是字符串时明确报错。无需手动循环调用 render，也没有 `file.md#view` 选择器。

筛选、排序、去重、诊断选择、历史截断仍由 Java 完成。模板只负责把准备好的数据写成消息，不进行业务计算。

## 换行与资源缓存

建议让控制标签独占一行。独占行的 `if`、`else`、`/if`、`for`、`/for` 会连同行缩进和换行一起消耗，不留下额外空行。正文中的空行会保留。行内条件也支持，但长段落不要挤多个标签。

模板文件的 CRLF、CR 统一为 LF；插入值的换行保持原样，因此原始 system prompt 的前缀可以逐字保留。

解析结果按资源名缓存，不提供热更新；修改资源需重启进程。这与模型服务的 token 前缀缓存是两回事。

## 错误与维护边界

资源缺失、非法 UTF-8、未知标签、未闭合或不匹配的块、重复 else、循环嵌套、参数或条目类型错误，抛出 `GenerationSdkException`，包含资源和必要位置。resource 或 model 本身为 null 时抛出 `NullPointerException`。

不支持 include、局部 View、metadata、对象反射、表达式和方法调用。不要按错误码、标题或单句拆文件；在完整 system/user 模板中按消息顺序维护文案。

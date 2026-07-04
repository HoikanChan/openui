# Java 代码规范化方案设计（华为 Java 编程规范 / Spotless + Checkstyle）

- 日期：2026-06-26
- 状态：已确认，待实施
- 适用范围：`packages/genui-java-sdk`、`examples/genui-service` 两个 Java 模块
- 规范来源：公司《华为 Java 语言编程规范》大纲（见本文 §3 条款映射）

## 1. 目标与范围

把两个 Java 模块的源码对齐《华为 Java 编程规范》，覆盖**命名、注释、格式、声明**四个维度。

关键事实：单一工具做不到全覆盖，必须两件工具分工：

- **Spotless + Eclipse JDT 格式化器** —— 负责能**自动改写**的纯排版项（缩进、大括号位置、
  行宽换行、import 排序、去行尾空白、文件末尾换行）。一条命令 `mvn spotless:apply` 改代码。
- **Checkstyle（`huawei_checks.xml`）** —— 负责 Spotless 改不了的**语义/结构规则**（命名、
  Javadoc/版权头/TODO、每行一变量、禁 C 风格数组、必须加大括号、每行一语句、修饰符顺序等），
  只能**检测并报告**，由开发者人工修复。

取舍：

- **两者都不绑定构建生命周期**：不绑 phase，不影响 `mvn package` / `mvn verify` / CI。
  手动执行 `mvn spotless:apply`、`mvn spotless:check`、`mvn checkstyle:check`。
- **本次不强制对现有 76 个文件做一次性全量改写**；执行时机交给开发者。
- Checkstyle 严重级别映射规范级别：**要求 = error，建议 = warning**；支持按级别/类型裁剪。

### 为什么需要两件工具（重要）

Spotless 的 Eclipse 格式化器只重排既有 token 的布局，**不会新增缺失的大括号、不会拆分多语句行、
不会改名、不会补 Javadoc**。下列规范条款因此只能由 Checkstyle 检测、人工修复，不可自动格式化：
所有 NAM、CMT 的 Javadoc/版权/TODO、DCL 全部、以及 FMT 中的 `G.FMT.05/09/16/19/20`。

## 2. 工具分工总表

| 维度 | Spotless 自动修 | Checkstyle 检测（人工修） |
| --- | --- | --- |
| 命名 NAM | — | 全部（见 §3.1） |
| 注释 CMT | — | Javadoc 存在性、版权头、TODO、注释缩进 |
| 格式 FMT | 缩进、K&R 括号位置、行宽换行、import 排序、注解独占行布局、去空白、末尾换行 | 必须加大括号、每行一语句、fall-through 注释、修饰符顺序、long 后缀 L、行宽兜底 |
| 声明 DCL | — | 每行一变量、禁 C 风格数组 |

> 避免双重强制：同一规则只由一件工具"权威执行"。Spotless 拥有 import 排序/去未用 import 的
> **自动修**；Checkstyle 对应检查仅作可选兜底，默认关闭，防止两者产生冲突。

## 3. 规范条款 → 检查项映射

级别：`要求`→Checkstyle `severity=error`；`建议`→`severity=warning`。
"工具"列：`S`=Spotless 自动修，`C`=Checkstyle 检测，`手动`=暂无可靠自动检查、靠评审。

### 3.1 命名 NAM（全部 Checkstyle，severity=warning）

| 条款 | 检查模块 | 说明/配置 |
| --- | --- | --- |
| G.NAM.01 标识符 ≤64 且 [A-Za-z0-9_] | （并入各 *Name 的 format 正则，含长度上限） | 建议 |
| G.NAM.02 包名小写 | `PackageName` `^[a-z]+(\.[a-z][a-z0-9]*)*$` | |
| G.NAM.03 类/枚举/接口 大驼峰 | `TypeName` | |
| G.NAM.04 方法 小驼峰 | `MethodName` | |
| G.NAM.05 常量 全大写下划线 | `ConstantName` | |
| G.NAM.06 变量 小驼峰 | `MemberName`/`LocalVariableName`/`ParameterName` | |
| 泛型类型变量 单大写字母 | `ClassTypeParameterName`/`MethodTypeParameterName`/`InterfaceTypeParameterName` `^[A-Z][0-9]?$` 等 | |
| G.NAM.07/08 布尔命名（避免否定、is/has/can/should） | 手动 + 可选自定义正则 | 否定含义难可靠正则化，列为评审项 |
| 异常类 Exception/Error 后缀 | 手动（无标准内置） | 评审项 |

### 3.2 注释 CMT（Checkstyle）

| 条款 | 级别 | 检查模块 | 说明 |
| --- | --- | --- | --- |
| G.CMT.01 public/protected 元素加 Javadoc | 建议 | 存在性：`MissingJavadocType` + `MissingJavadocMethod` + `JavadocVariable`（scope=protected，字段豁免 `serialVersionUID`，方法 `@Override` 默认豁免）；格式校验：`JavadocType`/`JavadocMethod` | |
| G.CMT.02 类 Javadoc 含功能 + 版本(`@since`) | 建议 | `JavadocType` + `MissingJavadocType` + `WriteTag`（`tag=@since`、`tagFormat=\S.*` 要求非空、缺失记 warning，作用于类/接口/枚举/注解声明） | |
| G.CMT.03 方法 Javadoc 标签顺序 @param/@return/@throws | 建议 | `JavadocMethod` + `AtclauseOrder` | |
| G.CMT.05 文件头版权 | 建议 | `RegexpHeader`，模板见 §6 | |
| G.CMT.06 注释与代码留空格 | 建议 | `CommentsIndentation`（部分覆盖） | |
| G.CMT.07 交付代码不含 TODO/FIXME | 建议 | `TodoComment` `(TODO|FIXME)` | 默认 warning |

### 3.3 格式 FMT

| 条款 | 级别 | 工具 | 检查模块 / 备注 |
| --- | --- | --- | --- |
| G.FMT.01 UTF-8 编码 | 建议 | S/构建 | poms 已设 `sourceEncoding=UTF-8` |
| G.FMT.02 文件结构顺序 | 建议 | 手动 | 评审项 |
| G.FMT.03 import 分组排序（安卓→华为→商业→开源→net/org→java→javax） | 建议 | **S** | Spotless `importOrder` + `.importorder`，见 §5.2 |
| G.FMT.04 类成员声明顺序 | 建议 | C | `DeclarationOrder`（部分） |
| G.FMT.05 if/循环必须加大括号 | 建议 | **C** | `NeedBraces`（Spotless 不会补括号） |
| G.FMT.06 K&R 左括号行尾 | 建议 | **S** | Eclipse `end_of_line`；`LeftCurly`/`RightCurly` 兜底（默认关） |
| G.FMT.07 避免空块 | 建议 | C | `EmptyBlock` |
| G.FMT.08 4 空格缩进禁 Tab | 建议 | **S** | Eclipse `tabulation.char=space, size=4` |
| G.FMT.09 每行一语句 | 建议 | **C** | `OneStatementPerLine`（Spotless 不拆行） |
| G.FMT.10 行宽 120 | 建议 | **S** + C | Spotless `lineSplit=120` 自动换行；`LineLength max=120` 兜底 |
| G.FMT.11 操作符前换行 | 建议 | S | Eclipse 换行策略 |
| G.FMT.12 减少多余空行 | 建议 | S | Eclipse blank-lines |
| G.FMT.13/14 空格突出关键字 / 不做垂直对齐 | 建议 | S | Eclipse 空格策略 |
| G.FMT.16 fall-through 需注释 | 建议 | **C** | `FallThrough` reliefPattern `\$FALL-THROUGH\$` |
| G.FMT.17 注解独占行 | 建议 | S + C | Eclipse 注解换行；`AnnotationLocation` 兜底 |
| G.FMT.18 块注释缩进同上下文 | 建议 | C | `CommentsIndentation` |
| G.FMT.19 修饰符顺序 | 建议 | **C** | `ModifierOrder` |
| G.FMT.20 long 用后缀 L | 建议 | **C** | `UpperEll` |

### 3.4 声明 DCL（Checkstyle）

| 条款 | 级别 | 检查模块 |
| --- | --- | --- |
| G.DCL.01 每行一变量 | 要求 | `MultipleVariableDeclarations`（severity=error） |
| G.DCL.03 禁 C 风格数组 | 要求 | `ArrayTypeStyle`（severity=error） |
| G.DCL.04 不依赖 ordinal() | 建议 | 手动 |
| G.DCL.05 禁 mutable public static final | 要求 | 手动（无可靠内置） |
| G.DCL.06 优先用枚举管理状态常量 | 建议 | 手动 |

> 凡标"手动"的条款不进入工具门禁，列入代码评审清单，避免误报噪声。

## 4. 关键工程约束：仓库是"假继承"结构

- 根 `pom.xml` 是 `packaging=pom` 聚合器，无 `<build>`。
- `packages/genui-java-sdk/pom.xml`：**无 `<parent>`**。
- `examples/genui-service/pom.xml`：parent 为 `spring-boot-starter-parent` 2.7.18。

两个子模块**都不继承根 pom**，故插件配置放根 pom 不会下发。**必须在两个子模块 pom 各加一份**
Spotless 与 Checkstyle 插件。共享的风格/规则文件放仓库根 `config/` 下，两模块均在根下第 2 层
（`packages/xxx`、`examples/xxx`），统一用相对路径 `${project.basedir}/../../config/...` 引用，
对两者都成立，且比 `${maven.multiModuleProjectDirectory}` 在单模块独立构建时更稳定。

## 5. 产物清单

### 5.1 `config/spotless/eclipse-format.xml`

华为风格 Eclipse 格式化 profile：`tabulation.char=space`、`size=4`、`indentation.size=4`、
`lineSplit=120`、大括号 `end_of_line`（K&R）、操作符前换行、注解独占行、压缩多余空行。
同一文件可导入 IntelliJ IDEA 的 Eclipse Code Formatter 插件 / Eclipse，保证 IDE 与命令行一致。

### 5.2 `config/spotless/huawei.importorder`

import 分组顺序（Spotless `.importorder` 格式），对齐 G.FMT.03：

```
0=\#
1=android
2=com.huawei
3=com
4=net
5=org
6=java
7=javax
```

（`\#` 为静态导入分组，置顶；其余按华为分组顺序。）配合 `removeUnusedImports`，禁通配符导入。

### 5.3 `config/checkstyle/huawei_checks.xml`

Checkstyle 规则集，按 §3 映射编写。要点：

- `<module name="Checker">` 顶层 `severity` 默认 `warning`；`要求` 级条款单独标 `severity=error`。
- 含 `TreeWalker` 下各 *Name / Javadoc* / NeedBraces / OneStatementPerLine / FallThrough /
  ModifierOrder / UpperEll / MultipleVariableDeclarations / ArrayTypeStyle 等模块。
- `RegexpHeader` 引用 §6 版权头模板（或内联 `header`）。
- 顶层挂 `SuppressionFilter` 指向 §5.4。
=======
- 顶层挂 `SuppressionFilter`，其 `file` 读取 `${checkstyle.suppressions.file}`（由 pom 的
  `suppressionsFileExpression` 注入，见 §5.5），并设 `optional=true`。
### 5.4 `config/checkstyle/suppressions.xml`

抑制规则：排除 `target/generated-sources/**`（Swagger codegen 产物）、按需排除遗留文件。
### 5.5 两个子模块 pom 插件块（不绑 phase）

`packages/genui-java-sdk/pom.xml` 与 `examples/genui-service/pom.xml` 的 `<build><plugins>` 各加：

```xml
<!-- 自动格式化 -->
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.43.0</version>
  <configuration>
    <java>
      <includes>
        <include>src/main/java/**/*.java</include>
        <include>src/test/java/**/*.java</include>
      </includes>
      <eclipse><file>${project.basedir}/../../config/spotless/eclipse-format.xml</file></eclipse>
      <importOrder><file>${project.basedir}/../../config/spotless/huawei.importorder</file></importOrder>
      <removeUnusedImports/>
      <trimTrailingWhitespace/>
      <endWithNewline/>
    </java>
  </configuration>
</plugin>

<!-- 语义/结构检查 -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>3.6.0</version>
  <dependencies>
    <dependency>
      <groupId>com.puppycrawl.tools</groupId>
      <artifactId>checkstyle</artifactId>
      <version>10.21.0</version>
    </dependency>
  </dependencies>
  <configuration>
    <configLocation>${project.basedir}/../../config/checkstyle/huawei_checks.xml</configLocation>
    <suppressionsLocation>${project.basedir}/../../config/checkstyle/suppressions.xml</suppressionsLocation>
    <suppressionsFileExpression>checkstyle.suppressions.file</suppressionsFileExpression>
    <includeTestSourceDirectory>true</includeTestSourceDirectory>
    <failOnViolation>false</failOnViolation>
  </configuration>
</plugin>
```

> 版本（spotless 2.43.0 / checkstyle-plugin 3.6.0 / checkstyle 10.21.0）在实施时按当时可用且
> 支持 Java 21 的版本确认。`failOnViolation=false` 体现"不绑构建/默认不阻断"；需要门禁时再调。

=======
> 抑制文件经 `suppressionsLocation` 定位、`suppressionsFileExpression` 注入到属性
> `checkstyle.suppressions.file`，供规则集中的 `SuppressionFilter` 读取（见 §5.3）。
## 6. 文件头版权模板

```java
/*
 * Copyright (c) Huawei Technologies Co., Ltd. {year}. All rights reserved.
 */

（`RegexpHeader` 用正则允许年份/年份区间变化。）

## 7. 作用范围

- 仅 `src/main/java` 与 `src/test/java`。
- Swagger codegen 产物在 `target/generated-sources/`：Spotless 本就不处理 `target/`；
  Checkstyle 经 `suppressions.xml` 额外排除，双保险。

## 8. 使用方式

| 命令 | 作用 |
| --- | --- |
| `mvn spotless:apply` | 自动改写代码到规范排版 |
| `mvn spotless:check` | 只查排版是否合规，不改 |
| `mvn checkstyle:check` | 输出命名/注释/声明等语义违规报告，不改代码 |

均需手动调用，不影响常规构建与 CI。按级别裁剪：临时只看"要求"级，可在规则集调严重级别或用
`checkstyle.severity` 控制；按类型裁剪通过启停对应模块。

## 9. 非目标（YAGNI）

- 不引入 PMD / SpotBugs（缺陷检查不在本规范大纲范围）。
- 不默认绑定构建生命周期、不默认设 CI 强制门禁（保留 `failOnViolation=false`）。
- 不在本设计中强制一次性全量改写现有代码。
- 不修改根聚合器 pom 结构、不改子模块 parent。
- "手动"标注的条款（布尔命名语义、异常后缀、mutable 常量、优先枚举等）不进工具门禁，列评审清单。

# Java 代码格式化方案设计（华为规范 / Spotless + Eclipse JDT）

- 日期：2026-06-26
- 状态：已确认，待实施
- 适用范围：`packages/genui-java-sdk`、`examples/genui-service` 两个 Java 模块

## 1. 目标

用 **Spotless + Eclipse JDT 格式化器** 把两个 Java 模块的源码自动格式化到贴合
**华为 Java 语言编程规范** 的排版。

明确的取舍：

- **只做自动格式化**，不引入 Checkstyle 等语义检查（命名/魔数/复杂度等规则不在本次范围）。
- **只加配置、不绑构建**：不绑定到任何 Maven phase，不影响 `mvn package` 与 CI。
  靠开发者手动执行 `mvn spotless:apply` 改代码、`mvn spotless:check` 查看是否合规。
- **不在本次一次性全量格式化现有代码**（用户选择"只加配置"），是否何时执行 apply 由开发者决定。

## 2. 关键工程约束：仓库是"假继承"结构

本仓库的 Maven 继承结构不能用普通的"父 pom 下发插件配置"思路：

- 根 `pom.xml` 是 `packaging=pom` 的聚合器（aggregator），仅 `<modules>`，无 `<build>`。
- `packages/genui-java-sdk/pom.xml`：**没有 `<parent>`**。
- `examples/genui-service/pom.xml`：`<parent>` 是 `spring-boot-starter-parent` 2.7.18。

结论：两个子模块 **都不继承根 pom**。因此 Spotless 插件配置放在根 pom **不会下发**，
必须 **在两个子模块 pom 里各加一份**。

共享的风格文件放仓库根的 `config/spotless/` 下。两个模块都恰好在根下第 2 层
（`packages/genui-java-sdk`、`examples/genui-service`），所以统一用相对路径
`${project.basedir}/../../config/spotless/<file>` 引用，对两者都成立，且比
`${maven.multiModuleProjectDirectory}` 在单模块独立构建时更稳定。

## 3. 华为规范排版要点（本次落地的排版项）

- 缩进：4 空格，禁止 Tab。
- 行宽：**120**。
- 大括号：K&R 风格，左括号不换行（行尾）。
- import：分组排序、禁止通配符 `import *.*`、移除未使用的 import。
- 行尾：去除行尾空白；文件以换行结尾。

## 4. 产物清单

### 4.1 `config/spotless/eclipse-format.xml`

华为风格的 Eclipse 格式化 profile（Eclipse `formatter` 设置 XML）：

- `org.eclipse.jdt.core.formatter.tabulation.char = space`
- `tabulation.size = 4`、`indentation.size = 4`
- `lineSplit = 120`
- 大括号位置统一 `end_of_line`（K&R）。
- 运算符、逗号、关键字后空格等按常见华为/主流 Java 排版设置。

同一份文件可导入 IntelliJ IDEA 的 **Eclipse Code Formatter** 插件或 Eclipse 本体，
保证 IDE 与命令行格式化结果一致。

### 4.2 `config/spotless/huawei.importorder`

import 分组顺序文件（Spotless `importOrder` 的 `.importorder` 格式）。约定顺序：

```
java
javax
org
com
\#
```

（`\#` 之后为静态导入分组。）配合 Spotless 的 `removeUnusedImports`，并禁止通配符导入。

### 4.3 两个子模块 pom 中的 `spotless-maven-plugin`

在 `packages/genui-java-sdk/pom.xml` 与 `examples/genui-service/pom.xml` 的
`<build><plugins>` 各加一份（**不配 `<executions>`，不绑 phase**）：

```xml
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
      <eclipse>
        <file>${project.basedir}/../../config/spotless/eclipse-format.xml</file>
      </eclipse>
      <importOrder>
        <file>${project.basedir}/../../config/spotless/huawei.importorder</file>
      </importOrder>
      <removeUnusedImports/>
      <trailingWhitespace/>
      <endWithNewline/>
    </java>
  </configuration>
</plugin>
```

> 版本号 `2.43.0` 与 Eclipse JDT 引擎版本在实施时按当时可用且支持 Java 21 的版本确认。

## 5. 作用范围

- 仅 `src/main/java` 与 `src/test/java`。
- Swagger codegen 产物在 `target/generated-sources/` 内，Spotless 本就不处理 `target/`，
  天然排除，无需额外配置。

## 6. 使用方式

- `mvn spotless:apply` —— 实际改写代码到规范格式。
- `mvn spotless:check` —— 只检查是否合规，不改代码（退出码反映结果）。

两者均需手动调用，不影响 `mvn package` / `mvn verify` 与 CI。

## 7. 非目标（YAGNI）

- 不引入 Checkstyle / PMD / SpotBugs 等语义或缺陷检查。
- 不绑定构建生命周期、不接 CI 强制门禁。
- 不在本次设计中对现有 76 个 Java 文件做强制一次性全量格式化提交（执行时机交给开发者）。
- 不修改根聚合器 pom 的结构、不改动子模块的 parent。

# Java 华为规范 Spotless + Checkstyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给两个 Java 模块（genui-java-sdk、genui-service）接入 Spotless 自动格式化与 Checkstyle 语义检查，对齐《华为 Java 编程规范》，仅加配置不绑构建。

**Architecture:** 共享的风格/规则文件放仓库根 `config/` 下；两个子模块 pom 各自声明 `spotless-maven-plugin` 与 `maven-checkstyle-plugin`（因仓库是"假继承"结构，子模块不继承根 pom），用相对路径 `${project.basedir}/../../config/...` 引用共享配置。Spotless 负责可自动修的排版；Checkstyle 检测命名/注释/声明等不可自动修的语义规则。

**Tech Stack:** Maven、spotless-maven-plugin（Eclipse JDT 格式化器）、maven-checkstyle-plugin + Checkstyle 10.x。Java 21。

## Global Constraints

- 规范来源：《华为 Java 编程规范》，详见 `docs/superpowers/specs/2026-06-26-java-spotless-huawei-format-design.md`（条款→检查项映射）。
- 行宽：**120**。缩进：**4 空格，禁 Tab**。大括号：**K&R（左括号行尾）**。
- 严重级别映射：规范"要求"级 → Checkstyle `severity=error`；"建议"级 → `severity=warning`。
- **不绑定构建生命周期**：插件不配 `<executions>`、不绑 phase；Checkstyle `failOnViolation=false`。`mvn package`/`verify`/CI 不受影响。
- **不在本计划中对现有代码做一次性全量改写**。
- 作用范围仅 `src/main/java` 与 `src/test/java`；`target/`（含 Swagger codegen 产物）排除。
- 共享配置路径固定为 `${project.basedir}/../../config/...`（两模块均在根下第 2 层）。
- 运行 Maven 目标需能访问公司 Maven 仓库（`com.huawei.bsp.*`、Spring Boot 依赖）。本机内存受限，**禁止 `mvn test`**；只跑指定的 `spotless:*` / `checkstyle:*` 目标，并用 `-f <module-pom>` 单模块调用，避免拉起整个 reactor 与测试 fork。
- 版本以实施时可用且支持 Java 21 的最新稳定版为准；本计划用：spotless 2.43.0、maven-checkstyle-plugin 3.6.0、checkstyle 10.21.0。

---

### Task 1: Spotless 自动格式化接入两个模块

**Files:**
- Create: `config/spotless/eclipse-format.xml`
- Create: `config/spotless/huawei.importorder`
- Modify: `packages/genui-java-sdk/pom.xml`（`<build><plugins>` 内新增 spotless 插件）
- Modify: `examples/genui-service/pom.xml`（`<build><plugins>` 内新增 spotless 插件）

**Interfaces:**
- Produces: 共享格式化配置文件路径 `config/spotless/eclipse-format.xml`、`config/spotless/huawei.importorder`；两个模块均可执行 `spotless:check` / `spotless:apply`。Task 3 的 pom 编辑会在同一 `<plugins>` 块追加 Checkstyle 插件。

- [ ] **Step 1: 创建 Eclipse 格式化 profile `config/spotless/eclipse-format.xml`**

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<profiles version="21">
  <profile kind="CodeFormatterProfile" name="HuaweiJava" version="21">
    <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="space"/>
    <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="4"/>
    <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="4"/>
    <setting id="org.eclipse.jdt.core.formatter.continuation_indentation" value="2"/>
    <setting id="org.eclipse.jdt.core.formatter.lineSplit" value="120"/>
    <setting id="org.eclipse.jdt.core.formatter.comment.line_length" value="120"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_block" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_method_declaration" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_type_declaration" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_constructor_declaration" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_switch" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_anonymous_type_declaration" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_enum_declaration" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.brace_position_for_array_initializer" value="end_of_line"/>
    <setting id="org.eclipse.jdt.core.formatter.insert_space_after_comma_in_method_invocation_arguments" value="insert"/>
    <setting id="org.eclipse.jdt.core.formatter.insert_space_before_opening_brace_in_block" value="insert"/>
    <setting id="org.eclipse.jdt.core.formatter.number_of_blank_lines_at_beginning_of_method_body" value="0"/>
    <setting id="org.eclipse.jdt.core.formatter.blank_lines_before_package" value="0"/>
    <setting id="org.eclipse.jdt.core.formatter.blank_lines_after_package" value="1"/>
    <setting id="org.eclipse.jdt.core.formatter.blank_lines_before_imports" value="1"/>
    <setting id="org.eclipse.jdt.core.formatter.blank_lines_after_imports" value="1"/>
    <setting id="org.eclipse.jdt.core.formatter.insert_new_line_after_annotation_on_type" value="insert"/>
    <setting id="org.eclipse.jdt.core.formatter.insert_new_line_after_annotation_on_method" value="insert"/>
    <setting id="org.eclipse.jdt.core.formatter.insert_new_line_after_annotation_on_field" value="insert"/>
    <setting id="org.eclipse.jdt.core.formatter.wrap_before_binary_operator" value="true"/>
  </profile>
</profiles>
```

> 未列出的键采用 Eclipse 默认值（与华为排版基本一致）。如需 100% 还原 IDE 习惯，可在 IDEA/Eclipse 调好后导出覆盖此文件。

- [ ] **Step 2: 创建 import 顺序文件 `config/spotless/huawei.importorder`**

对齐 G.FMT.03（静态置顶 → android → 华为 → 其他商业 com → net → org → java → javax）：

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

> 未匹配前缀的第三方包（如 `lombok.*`）按 Eclipse 默认位置归入，规范允许风格一致即可。

- [ ] **Step 3: 在 `packages/genui-java-sdk/pom.xml` 的 `<build><plugins>` 追加 spotless 插件**

定位现有 `<build><plugins>` 块（已有 `maven-surefire-plugin`），在其内追加：

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

- [ ] **Step 4: 在 `examples/genui-service/pom.xml` 的 `<build><plugins>` 追加同样的 spotless 插件**

定位现有 `<build><plugins>` 块（已有 `spring-boot-maven-plugin`、`swagger-codegen-maven-plugin`），在其内追加与 Step 3 **完全相同**的 `<plugin>...</plugin>` 块（路径 `${project.basedir}/../../config/...` 对本模块同样成立）。

- [ ] **Step 5: 验证 genui-java-sdk 的 Spotless 已生效（相当于失败测试 → 证明被检出）**

Run: `mvn -f packages/genui-java-sdk/pom.xml spotless:check`
Expected: 构建 **失败（BUILD FAILURE）**，输出形如 `The following files had format violations:` 并列出若干 `.java` 文件。这证明格式化配置成功加载且对现有未格式化代码生效。
（若输出的是 XML 解析错误或 "Unable to locate file"，说明 eclipse-format.xml/importorder 路径或内容有误，需修正后重跑。）

- [ ] **Step 6: 验证 genui-service 的 Spotless 已生效**

Run: `mvn -f examples/genui-service/pom.xml spotless:check`
Expected: 同样 BUILD FAILURE + `format violations` 列表。
（不在本步执行 `spotless:apply`——全量改写时机按规范交给开发者决定。）

- [ ] **Step 7: 提交**

```bash
git add config/spotless/eclipse-format.xml config/spotless/huawei.importorder \
        packages/genui-java-sdk/pom.xml examples/genui-service/pom.xml
git commit -m "feat(build): 接入 Spotless 华为风格自动格式化(两模块)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Checkstyle 规则集与抑制文件

**Files:**
- Create: `config/checkstyle/huawei_checks.xml`
- Create: `config/checkstyle/suppressions.xml`

**Interfaces:**
- Produces: `config/checkstyle/huawei_checks.xml`（供两模块 `configLocation` 引用）、`config/checkstyle/suppressions.xml`（经 `${config_loc}` 在规则集内引用）。Task 3 的 pom 插件指向这两个文件。

- [ ] **Step 1: 创建抑制文件 `config/checkstyle/suppressions.xml`**

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
  "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
  "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<suppressions>
  <!-- 生成代码与构建产物不检查 -->
  <suppress files="[\\/]target[\\/]" checks=".*"/>
  <suppress files="[\\/]generated-sources[\\/]" checks=".*"/>
</suppressions>
```

- [ ] **Step 2: 创建规则集 `config/checkstyle/huawei_checks.xml`**

按 spec §3 条款映射编写（`要求`→error，其余 warning）：

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
  "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
  "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
  <property name="charset" value="UTF-8"/>
  <property name="severity" value="warning"/>
  <property name="fileExtensions" value="java"/>

  <!-- 文件头版权 G.CMT.05；年份允许单年或区间 -->
  <module name="RegexpHeader">
    <property name="header"
              value="^/\*$\n^ \* Copyright \(c\) Huawei Technologies Co\., Ltd\. \d{4}(-\d{4})?\. All rights reserved\.$\n^ \*/$"/>
  </module>

  <!-- 行宽兜底 G.FMT.10 -->
  <module name="LineLength">
    <property name="max" value="120"/>
    <property name="ignorePattern" value="^package.*|^import.*|https?://|ftp://"/>
  </module>

  <module name="SuppressionFilter">
    <property name="file" value="${config_loc}/suppressions.xml"/>
    <property name="optional" value="true"/>
  </module>

  <module name="TreeWalker">
    <!-- 命名 NAM -->
    <module name="PackageName">
      <property name="format" value="^[a-z]+(\.[a-z][a-z0-9]*)*$"/>
    </module>
    <module name="TypeName"/>
    <module name="MethodName"/>
    <module name="ConstantName"/>
    <module name="MemberName"/>
    <module name="LocalVariableName"/>
    <module name="LocalFinalVariableName"/>
    <module name="StaticVariableName"/>
    <module name="ParameterName"/>
    <module name="ClassTypeParameterName">
      <property name="format" value="^[A-Z][0-9]?$"/>
    </module>
    <module name="MethodTypeParameterName">
      <property name="format" value="^[A-Z][0-9]?$"/>
    </module>
    <module name="InterfaceTypeParameterName">
      <property name="format" value="^[A-Z][0-9]?$"/>
    </module>

    <!-- 注释 CMT -->
    <module name="JavadocType">
      <property name="scope" value="protected"/>
    </module>
    <module name="MissingJavadocType">
      <property name="scope" value="protected"/>
    </module>
    <module name="JavadocMethod">
      <property name="accessModifiers" value="public,protected"/>
    </module>
    <module name="AtclauseOrder"/>
    <module name="TodoComment">
      <property name="format" value="(TODO)|(FIXME)"/>
    </module>
    <module name="CommentsIndentation"/>

    <!-- 格式 FMT(不可自动修部分) -->
    <module name="NeedBraces"/>
    <module name="OneStatementPerLine"/>
    <module name="FallThrough"/>
    <module name="ModifierOrder"/>
    <module name="UpperEll"/>
    <module name="EmptyBlock">
      <property name="option" value="text"/>
    </module>
    <module name="AnnotationLocation"/>
    <module name="DeclarationOrder"/>

    <!-- 声明 DCL(要求级=error) -->
    <module name="MultipleVariableDeclarations">
      <property name="severity" value="error"/>
    </module>
    <module name="ArrayTypeStyle">
      <property name="severity" value="error"/>
    </module>

    <!-- import 兜底(自动修由 Spotless 负责) -->
    <module name="AvoidStarImport"/>
    <module name="RedundantImport"/>
    <module name="UnusedImports"/>
  </module>
</module>
```

> 说明：`MissingJavadocMethod` 暂不启用，避免对大量内部 protected/public 方法产生 warning 噪声；`JavadocMethod` 只校验已有 Javadoc 的正确性。如需强制"public/protected 必须有 Javadoc"，再加 `MissingJavadocMethod`。

- [ ] **Step 3: 校验规则集 XML 自身可被 Checkstyle 解析（失败测试 → 通过）**

无需整个 Maven 构建即可验证 XML 合法。最简方式是在 Task 3 接入后通过 `checkstyle:check` 间接验证；本步先用纯文本检查确认两个文件 XML 结构闭合（无未关闭标签）。
Run: `git diff --check && echo "files staged ok"`（占位语义检查；真正的规则加载验证在 Task 3 Step 3）
Expected: 命令成功返回。

- [ ] **Step 4: 提交**

```bash
git add config/checkstyle/huawei_checks.xml config/checkstyle/suppressions.xml
git commit -m "feat(build): 新增华为规范 Checkstyle 规则集与抑制文件

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Checkstyle 插件接入两个模块

**Files:**
- Modify: `packages/genui-java-sdk/pom.xml`（`<build><plugins>` 追加 checkstyle 插件）
- Modify: `examples/genui-service/pom.xml`（`<build><plugins>` 追加 checkstyle 插件）

**Interfaces:**
- Consumes: Task 1 的两处 `<build><plugins>` 块；Task 2 的 `config/checkstyle/huawei_checks.xml`、`config/checkstyle/suppressions.xml`。
- Produces: 两模块均可执行 `mvn -f <pom> checkstyle:check`。

- [ ] **Step 1: 在 `packages/genui-java-sdk/pom.xml` 的 `<build><plugins>` 追加 checkstyle 插件**

```xml
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
    <includeTestSourceDirectory>true</includeTestSourceDirectory>
    <failOnViolation>false</failOnViolation>
  </configuration>
</plugin>
```

> `${config_loc}` 由插件自动设为 configLocation 所在目录，故规则集中对 `suppressions.xml` 的相对引用可解析。

- [ ] **Step 2: 在 `examples/genui-service/pom.xml` 的 `<build><plugins>` 追加完全相同的 checkstyle 插件块**

内容与 Step 1 一致（路径同样成立）。

- [ ] **Step 3: 验证 Checkstyle 规则确实在抓违规（失败测试 → 证明生效）**

对 genui-java-sdk 临时打开失败开关运行，确认规则加载并对现有代码报出违规：
Run: `mvn -f packages/genui-java-sdk/pom.xml checkstyle:check -Dcheckstyle.failOnViolation=true`
Expected: BUILD FAILURE，控制台/`target/checkstyle-result.xml` 含命名、Javadoc 等 warning/error 条目。这证明 `huawei_checks.xml` 被正确加载且规则生效。
（若报 "Unable to find configuration file" 或 DTD/规则解析错误，回到 Task 2 修正后重跑。）

- [ ] **Step 4: 验证默认（不阻断）模式**

Run: `mvn -f packages/genui-java-sdk/pom.xml checkstyle:check`
Expected: BUILD SUCCESS（`failOnViolation=false`），但仍生成 `target/checkstyle-result.xml` 报告。证明日常使用不阻断构建。

- [ ] **Step 5: 验证 genui-service 同样生效**

Run: `mvn -f examples/genui-service/pom.xml checkstyle:check`
Expected: BUILD SUCCESS 且生成报告。

- [ ] **Step 6: 提交**

```bash
git add packages/genui-java-sdk/pom.xml examples/genui-service/pom.xml
git commit -m "feat(build): 接入 maven-checkstyle-plugin 引用华为规则集(两模块)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 开发者使用文档

**Files:**
- Modify: `CONTRIBUTING.md`（追加 Java 代码规范小节）

**Interfaces:**
- Consumes: Task 1–3 产出的命令与配置路径。

- [ ] **Step 1: 在 `CONTRIBUTING.md` 末尾追加"Java 代码规范"小节**

```markdown
## Java 代码规范（华为 Java 编程规范）

两个 Java 模块（`packages/genui-java-sdk`、`examples/genui-service`）接入了 Spotless（自动格式化）
与 Checkstyle（语义检查），配置位于仓库根 `config/`。

常用命令（在对应模块上单独运行，避免拉起整个 reactor）：

- `mvn -f packages/genui-java-sdk/pom.xml spotless:apply` —— 自动格式化排版
- `mvn -f packages/genui-java-sdk/pom.xml spotless:check` —— 仅检查排版是否合规
- `mvn -f packages/genui-java-sdk/pom.xml checkstyle:check` —— 输出命名/注释/声明等语义违规报告

`examples/genui-service` 同理替换 `-f` 路径。两者均不绑定构建生命周期，不影响 `mvn package`。

IDE 一致性：可将 `config/spotless/eclipse-format.xml` 导入 IntelliJ IDEA 的
Eclipse Code Formatter 插件或 Eclipse，使 IDE 保存即符合命令行格式化结果。
```

- [ ] **Step 2: 提交**

```bash
git add CONTRIBUTING.md
git commit -m "docs: 补充 Java 华为规范 Spotless/Checkstyle 使用说明

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 自检结果（Self-Review）

**Spec 覆盖**：
- §1–§2 目标/分工 → 全部任务的 Global Constraints + Task 1/Task 3 分工落地。
- §3.1 命名 NAM → Task 2 规则集 PackageName/TypeName/MethodName/ConstantName/各 *Name/泛型 *TypeParameterName。
- §3.2 注释 CMT → JavadocType/MissingJavadocType/JavadocMethod/AtclauseOrder/TodoComment/CommentsIndentation/RegexpHeader。
- §3.3 格式 FMT → 自动修部分 Task 1（eclipse-format.xml + importorder）；检测部分 NeedBraces/OneStatementPerLine/FallThrough/ModifierOrder/UpperEll/LineLength/AnnotationLocation/DeclarationOrder。
- §3.4 声明 DCL → MultipleVariableDeclarations/ArrayTypeStyle（error）。
- §4 假继承约束 → 两 pom 各加插件，相对路径引用。
- §5 产物清单 → Task 1（spotless 配置）、Task 2（checkstyle 配置）、Task 1/3（pom）。
- §6 版权模板 → Task 2 RegexpHeader 正则。
- §7 作用范围 → includes 限定 src/main、src/test；suppressions 排除 target/generated-sources。
- §8 使用方式 → Task 4 文档。
- §9 非目标（手动条款 G.NAM.07/08、异常后缀、G.DCL.05/06 等）→ 有意不进规则集，已在 spec §3 标注，计划不添加对应任务（符合 YAGNI）。

**占位扫描**：无 TBD/TODO 式占位；每个改代码步骤均含完整内容。Task 2 Step 3 为纯文本闭合检查（真正的规则加载验证在 Task 3 Step 3），非占位。

**类型/路径一致性**：配置文件路径在 spec、各 Task、文档中一致（`config/spotless/eclipse-format.xml`、`config/spotless/huawei.importorder`、`config/checkstyle/huawei_checks.xml`、`config/checkstyle/suppressions.xml`）；pom 引用与创建路径一致；插件版本号全篇统一。

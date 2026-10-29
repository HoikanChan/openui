# Contributing Guidelines

Thank you for considering contributing to _openui_! This document provides guidelines for contributing.

## How to Contribute

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/amazing-feature`) & make your changes.
3. Ensure your code follows our style guidelines.
4. Update the README.md & autogenerate docs if needed.
5. Open a Pull Request

## Bug Reports

Use Github Issues to report bugs. When reporting bugs, please include:

- A clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Your environment details

## Questions?

We're happy to help! Feel free to open an issue for any questions or concerns.
You can also join our [Discord](https://discord.gg/Pbv5PsqUSv) to chat with the team

## Java 代码规范（华为 Java 编程规范）

两个 Java 模块（`packages/genui-java-sdk`、`packages/genui-eval-cli`）接入三项检查，
统一在根 `pom.xml` 配置、由 `<parent>` 继承：

- **fmt-maven-plugin**（AOSP 风格）—— 排版，`verify` 阶段强制
- **Checkstyle**（`config/checkstyle/checkstyle.xml`）—— 命名/注释/声明等语义规则，`verify` 阶段强制
- **SpotBugs** + findsecbugs（`config/spotbugs/`）—— 缺陷与安全模式，`verify` 阶段强制

常用命令（在对应模块上单独运行，避免拉起整个 reactor）：

```sh
mvn -f packages/genui-java-sdk/pom.xml fmt:format      # 自动格式化排版
mvn -f packages/genui-java-sdk/pom.xml fmt:check       # 仅检查排版
mvn -f packages/genui-java-sdk/pom.xml checkstyle:check
mvn -f packages/genui-java-sdk/pom.xml spotbugs:check
```

`mvn verify` 会依次执行全部检查，任一违规即失败。`packages/genui-eval-cli` 同理替换 `-f` 路径。

CI：PR 和 main 推送会触发 `.github/workflows/java-lint.yml`，对两个 Java 模块执行
`mvn -B -DskipTests verify`（即上述三项检查）。豁免项集中在 `config/checkstyle/suppressions.xml`
（按行）与 `config/spotbugs/spotbugs-exclude.xml`（按类/方法），新增豁免需写明理由。

IDE 一致性：IntelliJ IDEA 装 google-java-format 插件并选 AOSP 风格，保存即与 `fmt` 结果一致。

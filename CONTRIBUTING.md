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

两个 Java 模块（`packages/genui-java-sdk`、`examples/genui-service`）接入了 Spotless（自动格式化）
与 Checkstyle（语义检查），配置位于仓库根 `config/`。

常用命令（在对应模块上单独运行，避免拉起整个 reactor）：

- `mvn -f packages/genui-java-sdk/pom.xml spotless:apply` —— 自动格式化排版
- `mvn -f packages/genui-java-sdk/pom.xml spotless:check` —— 仅检查排版是否合规
- `mvn -f packages/genui-java-sdk/pom.xml checkstyle:check` —— 输出命名/注释/声明等语义违规报告

`examples/genui-service` 同理替换 `-f` 路径。两者均不绑定构建生命周期，不影响 `mvn package`。

IDE 一致性：可将 `config/spotless/eclipse-format.xml` 导入 IntelliJ IDEA 的
Eclipse Code Formatter 插件或 Eclipse，使 IDE 保存即符合命令行格式化结果。

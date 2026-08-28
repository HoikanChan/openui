package com.huawei.cloudsop.genui.core.validation.report;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Command-line report generator for the checked-in {@code validation-test} corpus. */
public final class ValidationCorpusReportGenerator {

    private static final Pattern ANNOTATION = Pattern.compile("\\\"id\\\":\\s*\\\"([^\\\"]+)\\\"\\s*,(.*)$");

    private ValidationCorpusReportGenerator() {
    }

    /** Generate one Markdown report from an annotation file followed by one or more JSON case files. */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "usage: ValidationCorpusReportGenerator <output.md> <error_detail.txt> <case.json>...");
        }

        Path output = Path.of(args[0]);
        Map<String, String> annotations = readAnnotations(Path.of(args[1]));
        GenerationContract contract = GenerationContractLoader.loadDefault();
        List<CaseResult> results = new ArrayList<>();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> rawIssueCounts = new TreeMap<>();
        Map<String, Integer> actionableIssueCounts = new TreeMap<>();
        Map<String, Integer> coverageCounts = new LinkedHashMap<>();

        for (int fileIndex = 2; fileIndex < args.length; fileIndex++) {
            Path source = Path.of(args[fileIndex]);
            List<Object> cases = Json.asList(Json.parse(Files.readString(source)), source.toString());
            for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
                Map<String, Object> testCase = Json.asObject(cases.get(caseIndex), "case");
                String id = String.valueOf(testCase.get("id"));
                ValidationResult validation = DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder()
                        .dsl(String.valueOf(testCase.get("dsl"))).contract(contract)
                        .dataModel((Map<String, Object>) testCase.get("dataModel")).mode(ValidationMode.FINAL).build());
                CaseResult result = new CaseResult(id, source.getFileName().toString(), caseIndex + 1,
                        annotations.get(id), String.valueOf(testCase.get("dsl")),
                        (Map<String, Object>) testCase.get("dataModel"), validation);
                results.add(result);
                statusCounts.merge(validation.status().name(), 1, Integer::sum);
                validation.issues().forEach(issue -> rawIssueCounts.merge(issue.code(), 1, Integer::sum));
                validation.actionableIssues()
                        .forEach(issue -> actionableIssueCounts.merge(issue.code(), 1, Integer::sum));
                coverageCounts.merge(coverage(result), 1, Integer::sum);
            }
        }

        Files.writeString(output,
                normalizeMarkdown(render(results, statusCounts, rawIssueCounts, actionableIssueCounts,
                        coverageCounts)),
                StandardCharsets.UTF_8);
        System.out.println("wrote=" + output.toAbsolutePath());
        System.out.println("cases=" + results.size());
        System.out.println("statuses=" + statusCounts);
        System.out.println("rawIssues=" + rawIssueCounts);
        System.out.println("actionableIssues=" + actionableIssueCounts);
        System.out.println("coverage=" + coverageCounts);
    }

    private static String render(List<CaseResult> results, Map<String, Integer> statusCounts,
            Map<String, Integer> rawIssueCounts, Map<String, Integer> actionableIssueCounts,
            Map<String, Integer> coverageCounts) {
        StringBuilder report = new StringBuilder();
        report.append("# validation-test 真实校验结果\n\n");
        report.append("- Validator: `DefaultOpenuiLangValidator.INSTANCE`\n");
        report.append("- Mode: `FINAL`\n");
        report.append("- Contract: 当前 Java SDK `openui/base-contract.json`\n");
        report.append("- 总用例数: ").append(results.size()).append("\n");
        report.append("- 状态统计: `").append(statusCounts).append("`\n");
        report.append("- 原始 Issue 总数: ").append(total(rawIssueCounts)).append("\n");
        report.append("- 可操作 Issue 总数: ").append(total(actionableIssueCounts)).append("\n");
        report.append("- 原始 Issue 统计: `").append(rawIssueCounts).append("`\n");
        report.append("- 可操作 Issue 统计: `").append(actionableIssueCounts).append("`\n");
        report.append("- 标注覆盖: `").append(coverageCounts).append("`\n\n");
        report.append("每个结果均由当前代码实际运行生成。`actionableIssues` 用于修复与报告；")
                .append("`rawIssues` 保留完整诊断和跨语言 parity。\n\n");

        for (int index = 0; index < results.size(); index++) {
            appendCase(report, index + 1, results.get(index));
        }
        return report.toString();
    }

    private static void appendCase(StringBuilder report, int index, CaseResult item) {
        ValidationResult validation = item.validation();
        List<ValidationIssue> actionable = validation.actionableIssues();
        report.append("## ").append(index).append(". ").append(item.id()).append("\n\n");
        report.append("- 来源: `").append(item.source()).append("` 第 ").append(item.sourceIndex()).append(" 条\n");
        report.append("- 状态: `").append(validation.status()).append("`\n");
        report.append("- 原始 Issue 数: ").append(validation.issues().size()).append("\n");
        report.append("- 可操作 Issue 数: ").append(actionable.size()).append("\n\n");
        report.append("- `error_detail.txt` 标注: ")
                .append(item.annotation() == null || item.annotation().isBlank() ? "（无说明）" : item.annotation())
                .append("\n");
        report.append("- 标注覆盖结果: `").append(coverage(item)).append("`\n\n");
        report.append("### 输入 DSL\n\n````openui\n").append(item.dsl()).append("\n````\n\n");
        report.append("### 输入 dataModel\n\n````json\n").append(Json.stringifyPretty(item.dataModel()))
                .append("\n````\n\n");
        report.append("### 可操作校验结果\n\n");
        appendIssues(report, validation.status().name(), actionable);
        if (!actionable.equals(validation.issues())) {
            report.append("<details>\n<summary>原始校验结果（含级联诊断）</summary>\n\n");
            appendIssues(report, validation.status().name(), validation.issues());
            report.append("</details>\n\n");
        }
    }

    private static void appendIssues(StringBuilder report, String status, List<ValidationIssue> issues) {
        report.append("````text\nstatus=").append(status).append("\n");
        if (issues.isEmpty()) {
            report.append("issues=[]\n");
        } else {
            for (int index = 0; index < issues.size(); index++) {
                ValidationIssue issue = issues.get(index);
                report.append("\nissue[").append(index).append("]\n");
                field(report, "code", issue.code());
                field(report, "severity", issue.severity());
                field(report, "source", issue.source());
                field(report, "statementId", issue.statementId());
                field(report, "component", issue.component());
                field(report, "path", issue.path());
                field(report, "line", issue.line());
                field(report, "column", issue.column());
                field(report, "retryable", issue.retryable());
                field(report, "message", issue.message());
                field(report, "hint", issue.hint());
            }
        }
        report.append("````\n\n");
    }

    private static Map<String, String> readAnnotations(Path path) throws Exception {
        Map<String, String> annotations = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher matcher = ANNOTATION.matcher(line.trim());
            if (matcher.find()) {
                annotations.put(matcher.group(1), matcher.group(2).trim());
            }
        }
        return annotations;
    }

    private static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String normalizeMarkdown(String markdown) {
        return markdown.lines().map(String::stripTrailing).collect(Collectors.joining("\n")).stripTrailing()
                + "\n";
    }

    private static String coverage(CaseResult item) {
        String annotation = item.annotation();
        if (annotation == null || annotation.isBlank()) {
            return "UNLABELED";
        }
        if (annotation.contains("不用关注")) {
            return "IGNORED";
        }
        return item.validation().hasBlockingIssues() ? "DETECTED" : "MISSED";
    }

    private static void field(StringBuilder output, String name, Object value) {
        output.append(name).append('=').append(value == null ? "<null>" : value).append('\n');
    }

    private record CaseResult(String id, String source, int sourceIndex, String annotation, String dsl,
            Map<String, Object> dataModel, ValidationResult validation) {
    }
}

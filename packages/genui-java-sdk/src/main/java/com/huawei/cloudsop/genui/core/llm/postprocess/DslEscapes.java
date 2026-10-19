package com.huawei.cloudsop.genui.core.postprocess;

/**
 * DSL字符串转义工具。用于将字符串安全嵌入DSL字面量时转义特殊字符。
 *
 * @since 2026-07-24
 */
public final class DslEscapes {

    private DslEscapes() {
    }

    /**
     * 转义字符串中的反斜杠和双引号，使其可安全嵌入DSL字符串字面量。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    public static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

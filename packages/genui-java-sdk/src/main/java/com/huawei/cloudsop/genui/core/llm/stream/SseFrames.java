/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.stream;

/**
 * 把内容编码为 SSE 帧文本，供测试与回放构造流式响应。
 *
 * @since 2026
 */
public final class SseFrames {
    private SseFrames() {}

    /**
     * 将内容按行编码为 SSE 帧：每行加 {@code data: } 前缀，最后补一个空行。换行符按 {@code \R} 识别，null 按字符串 "null" 处理。
     *
     * @param content 帧内容
     * @return SSE 文本
     */
    public static String of(String content) {
        StringBuilder frame = new StringBuilder();
        for (String line : String.valueOf(content).split("\\R", -1)) {
            frame.append("data: ").append(line).append('\n');
        }
        frame.append('\n');
        return frame.toString();
    }

    /**
     * 构造流结束帧 {@code data: [DONE]}。
     *
     * @return 结束帧文本
     */
    public static String done() {
        return "data: [DONE]\n\n";
    }
}

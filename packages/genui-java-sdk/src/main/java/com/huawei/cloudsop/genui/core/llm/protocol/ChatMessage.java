/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

/**
 * chat completion 消息：角色与正文。
 *
 * @param role 角色，如 system、user
 * @param content 消息正文
 * @since 2026
 */
public record ChatMessage(String role, String content) {
    /**
     * 构造 system 角色的消息。
     *
     * @param content 消息正文
     * @return system 消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * 构造 user 角色的消息。
     *
     * @param content 消息正文
     * @return user 消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}

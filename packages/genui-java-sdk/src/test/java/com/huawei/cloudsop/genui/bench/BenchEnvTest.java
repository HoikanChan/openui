/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * {@link BenchEnv} 的 .env 解析：文件可选；{@code KEY=VALUE} 解析；注释 / 空行 / 无等号行跳过。
 *
 * @since 2026
 */
class BenchEnvTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileYieldsEmpty() throws Exception {
        Map<String, String> resolved = BenchEnv.resolve(tempDir.resolve(".env"));
        // 不能断言完全为空（真实环境变量可能命中 KEYS），只需不抛异常且不包含文件键。
        assertTrue(BenchEnv.readDotEnv(tempDir.resolve(".env")).isEmpty());
        assertEquals(resolved.keySet().stream().filter(key -> System.getenv(key) == null).count(), 0);
    }

    @Test
    void parsesKeyValueAndSkipsComments() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "# comment line\n" + "\n" + "LLM_BASE_URL=https://llm.example/v1\n" + "NO_EQUALS_LINE\n"
                + "LLM_MODEL= qwen3.6-27b \n" + "OTHER_KEY=ignored-but-parsed\n", StandardCharsets.UTF_8);

        Map<String, String> fileValues = BenchEnv.readDotEnv(env);
        assertEquals("https://llm.example/v1", fileValues.get("LLM_BASE_URL"));
        assertEquals("qwen3.6-27b", fileValues.get("LLM_MODEL"));
        assertEquals("ignored-but-parsed", fileValues.get("OTHER_KEY"));
        assertEquals(3, fileValues.size());
    }

    @Test
    void resolveMergesOnlyBenchKeys() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env,
                "LLM_BASE_URL=https://llm.example/v1\nLLM_API_KEY=sk-test\nLLM_MODEL=m1\n" + "UNRELATED=nope\n",
                StandardCharsets.UTF_8);

        Map<String, String> resolved = BenchEnv.resolve(env);
        // 环境变量优先于文件：本测试不依赖具体环境，只断言文件值在环境变量缺席时进入结果。
        if (System.getenv("LLM_BASE_URL") == null) {
            assertEquals("https://llm.example/v1", resolved.get("LLM_BASE_URL"));
        }
        if (System.getenv("LLM_MODEL") == null) {
            assertEquals("m1", resolved.get("LLM_MODEL"));
        }
        assertTrue(!resolved.containsKey("UNRELATED"), "non-bench keys must not leak into the resolved view");
    }
}

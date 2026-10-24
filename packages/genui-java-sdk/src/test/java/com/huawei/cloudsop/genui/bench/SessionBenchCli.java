/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;

/** One live public session; newline JSON on stdout, ready/start handshake with a preloaded real browser consumer. */
public final class SessionBenchCli {
    private SessionBenchCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "SessionBenchCli <cases-dir> <case-id> <env-file> <live-initial|historical-initial-replay>");
        var benchCase = RepairCasePack.load(Path.of(args[0]), null).stream().filter(c -> c.id().equals(args[1]))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown case id " + args[1]));
        var env = BenchEnv.resolve(Path.of(args[2]));
        if (env.get("LLM_MODEL") == null)
            throw new IllegalArgumentException("LLM_MODEL missing");
        var transport = new OpenAiCompatBenchTransport(env.get("LLM_BASE_URL"), env.get("LLM_API_KEY"),
                env.get("HTTPS_PROXY"));
        if (!java.util.Set.of("live-initial", "historical-initial-replay").contains(args[3]))
            throw new IllegalArgumentException("Unknown mode");
        var runner = new SessionBenchRunner(transport, env.get("LLM_MODEL"),
                "historical-initial-replay".equals(args[3]));
        System.out.println(Json.stringify(Map.of("type", "ready")));
        System.out.flush();
        String start = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))
                .readLine();
        if (!"start".equals(start))
            throw new IllegalArgumentException("Expected start handshake");
        var result = runner.run(benchCase, event -> {
            System.out.println(Json.stringify(event));
            System.out.flush();
        });
        System.out.println(Json.stringify(result));
        System.out.flush();
    }
}

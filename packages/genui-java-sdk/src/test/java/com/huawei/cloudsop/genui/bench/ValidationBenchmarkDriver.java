/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A/B benchmark driver for Generated DSL Validation.
 *
 * <p>Reads react-ui-dsl benchmark fixtures (meta.prompt + data), generates DSL through
 * {@link GenUiGenerator#generateStream} against an OpenAI-compatible endpoint, and writes
 * per-fixture DSL outputs plus timing/call metrics. Two arms:
 *
 * <ul>
 *   <li>{@code off} — {@link GenUiValidationConfig#disabled()}: raw delta streaming, no gate.</li>
 *   <li>{@code on} — {@link GenUiValidationConfig#streamingGateWithReask()}: statement gate + Fail-Fast Reask.</li>
 * </ul>
 *
 * <p>Usage:
 * {@code java ... ValidationBenchmarkDriver --fixtures <dir> --out <dir> --arm off|on --env <.env path>
 * [--only id1,id2] [--concurrency 3] [--capture-body <file>]}
 */
public final class ValidationBenchmarkDriver {
    private ValidationBenchmarkDriver() {
    }

    // ── OpenAI-compatible transport ──────────────────────────────────────────

    /** Streams chat/completions from an OpenAI-compatible endpoint via java.net.http. */
    static final class OpenAiHttpTransport implements LlmTransport {
        private final HttpClient client;
        private final String url;
        private final String apiKey;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> firstBody = new AtomicReference<>();
        final List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());

        OpenAiHttpTransport(String baseUrl, String apiKey, String httpsProxy) {
            this.url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            this.apiKey = apiKey;
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30));
            if (httpsProxy != null && !httpsProxy.isBlank()) {
                URI proxy = URI.create(httpsProxy);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            }
            this.client = builder.build();
        }

        @Override
        public String post(String body) throws LlmTransportException {
            try (InputStream stream = postStream(body)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new LlmTransportException("Failed reading LLM response: " + e.getMessage());
            }
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            calls.incrementAndGet();
            firstBody.compareAndSet(null, body);
            bodies.add(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(600))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    String detail;
                    try (InputStream err = response.body()) {
                        detail = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    throw new LlmTransportException(
                            "LLM request failed, status " + response.statusCode() + ": " + truncate(detail, 500));
                }
                return response.body();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new LlmTransportException("LLM request failed: " + e.getMessage());
            }
        }
    }

    // ── Fixture model ────────────────────────────────────────────────────────

    record Fixture(String id, String prompt, Map<String, Object> data, String skipReason) {
    }

    @SuppressWarnings("unchecked")
    static Fixture loadFixture(Path file) throws IOException {
        String id = file.getFileName().toString().replaceFirst("\\.json$", "");
        Map<String, Object> envelope = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)),
                "fixture " + id);
        Map<String, Object> meta = Json.asObject(envelope.get("meta"), "meta of " + id);
        String prompt = (String) meta.get("prompt");
        Object data = envelope.get("data");
        if (!(data instanceof Map)) {
            return new Fixture(id, prompt, null,
                    "top-level data is " + (data == null ? "null" : data.getClass().getSimpleName())
                            + "; Java SDK data model requires an object");
        }
        return new Fixture(id, prompt, (Map<String, Object>) data, null);
    }

    // ── Per-fixture run ──────────────────────────────────────────────────────

    static Map<String, Object> runFixture(Fixture fixture, String arm, GenUiLlmConfig llmConfig, String baseUrl,
            String apiKey, String proxy, Path outDir, Path captureBody) {
        LinkedHashMap<String, Object> metric = new LinkedHashMap<>();
        metric.put("id", fixture.id());
        metric.put("arm", arm);
        if (fixture.skipReason() != null) {
            metric.put("status", "SKIPPED");
            metric.put("skipReason", fixture.skipReason());
            return metric;
        }

        OpenAiHttpTransport transport = new OpenAiHttpTransport(baseUrl, apiKey, proxy);
        GenUiValidationConfig validationConfig = "on".equals(arm)
                ? GenUiValidationConfig.streamingGateWithReask()
                : GenUiValidationConfig.disabled();
        GenUiGenerator generator = GenUiGenerator.withTransport(llmConfig, transport, validationConfig, null);

        UiGenerationRequest request = UiGenerationRequest.builder()
                .userInput(fixture.prompt())
                .response(fixture.data())
                .build();

        List<String> errorFrames = new ArrayList<>();
        long[] firstDslNanos = {-1};
        long start = System.nanoTime();
        try {
            GenUiGenerationResult result = generator.generateStream(request, envelope -> {
                if (RenderStreamEnvelope.TYPE_DSL.equals(envelope.type()) && firstDslNanos[0] < 0) {
                    firstDslNanos[0] = System.nanoTime();
                }
                if (RenderStreamEnvelope.TYPE_ERROR.equals(envelope.type())) {
                    errorFrames.add(String.valueOf(envelope.content()));
                }
            });
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            String dsl = result.dsl() == null ? "" : result.dsl();
            Files.writeString(outDir.resolve(fixture.id() + ".dsl"), dsl, StandardCharsets.UTF_8);

            metric.put("status", result.validationStatus() == null ? "NONE" : result.validationStatus().name());
            metric.put("durationMs", durationMs);
            metric.put("firstDslMs", firstDslNanos[0] < 0 ? null : (firstDslNanos[0] - start) / 1_000_000);
            metric.put("llmCalls", transport.calls.get());
            metric.put("reasks", Math.max(0, transport.calls.get() - 1));
            metric.put("dslChars", dsl.length());
            if (!errorFrames.isEmpty()) {
                metric.put("errorFrames", errorFrames);
            }
        } catch (RuntimeException | IOException e) {
            metric.put("status", "EXCEPTION");
            metric.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            metric.put("llmCalls", transport.calls.get());
            metric.put("error", truncate(e.getMessage(), 800));
        }

        if (captureBody != null && transport.firstBody.get() != null) {
            try {
                Files.writeString(captureBody, transport.firstBody.get(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // capture is best-effort
            }
        }
        writeCallLog(fixture, transport, errorFrames, outDir);
        return metric;
    }

    /**
     * When a fixture needed more than one LLM call (i.e. reask fired), persist every request body so the
     * erroneous DSL, the reask instructions, and the full prompts can be inspected afterwards.
     */
    private static void writeCallLog(Fixture fixture, OpenAiHttpTransport transport, List<String> errorFrames,
            Path outDir) {
        if (transport.bodies.size() <= 1) {
            return;
        }
        try {
            LinkedHashMap<String, Object> log = new LinkedHashMap<>();
            log.put("id", fixture.id());
            log.put("llmCalls", transport.bodies.size());
            if (!errorFrames.isEmpty()) {
                log.put("errorFrames", errorFrames);
            }
            List<Object> calls = new ArrayList<>();
            for (int i = 0; i < transport.bodies.size(); i++) {
                LinkedHashMap<String, Object> call = new LinkedHashMap<>();
                call.put("index", i);
                call.put("kind", i == 0 ? "initial" : "reask");
                call.put("request", Json.parse(transport.bodies.get(i)));
                calls.add(call);
            }
            log.put("calls", calls);
            Files.writeString(outDir.resolve(fixture.id() + ".llm-calls.json"), Json.stringifyPretty(log),
                    StandardCharsets.UTF_8);
        } catch (RuntimeException | IOException ignored) {
            // forensic log is best-effort; never fail the benchmark run for it
        }
    }

    // ── Main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        Path fixturesDir = Path.of(require(opts, "fixtures"));
        Path outDir = Path.of(require(opts, "out"));
        String arm = require(opts, "arm");
        if (!Set.of("off", "on").contains(arm)) {
            throw new IllegalArgumentException("--arm must be off|on");
        }
        Map<String, String> env = loadEnv(Path.of(require(opts, "env")));
        String apiKey = requireEnv(env, "LLM_API_KEY");
        String baseUrl = requireEnv(env, "LLM_BASE_URL");
        String model = opts.getOrDefault("model", requireEnv(env, "LLM_MODEL"));
        String proxy = env.get("HTTPS_PROXY");
        int concurrency = Integer.parseInt(opts.getOrDefault("concurrency", "3"));
        Set<String> only = opts.containsKey("only") ? Set.of(opts.get("only").split(",")) : null;
        Path captureBody = opts.containsKey("capture-body") ? Path.of(opts.get("capture-body")) : null;

        double temperature = Double.parseDouble(opts.getOrDefault("temperature", "0"));
        GenUiLlmConfig llmConfig = GenUiLlmConfig.builder()
                .defaultModel(model)
                .temperature(temperature)
                .enableThinking(false)
                .build();

        List<Fixture> fixtures = new ArrayList<>();
        try (var files = Files.list(fixturesDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                Fixture fixture = loadFixture(file);
                if (only == null || only.contains(fixture.id())) {
                    fixtures.add(fixture);
                }
            }
        }
        Files.createDirectories(outDir);

        System.out.printf("arm=%s model=%s temp=%s fixtures=%d concurrency=%d%n", arm, model, temperature, fixtures.size(), concurrency);
        Instant startedAt = Instant.now();
        long wallStart = System.nanoTime();

        List<Map<String, Object>> metrics = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                futures.add(pool.submit(() -> {
                    Map<String, Object> m = runFixture(fixture, arm, llmConfig, baseUrl, apiKey, proxy, outDir,
                            captureBody);
                    synchronized (System.out) {
                        System.out.printf("  [%s] %s status=%s duration=%sms calls=%s%n", arm, fixture.id(),
                                m.get("status"), m.getOrDefault("durationMs", "-"), m.getOrDefault("llmCalls", "-"));
                    }
                    return m;
                }));
            }
            for (Future<Map<String, Object>> future : futures) {
                metrics.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }
        metrics.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("arm", arm);
        summary.put("model", model);
        summary.put("startedAt", startedAt.toString());
        summary.put("totalWallMs", (System.nanoTime() - wallStart) / 1_000_000);
        summary.put("fixtures", metrics);
        Path metricsFile = outDir.resolve("metrics-" + arm + ".json");
        Files.writeString(metricsFile, Json.stringifyPretty(summary), StandardCharsets.UTF_8);
        System.out.println("metrics written to " + metricsFile);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static Map<String, String> parseArgs(String[] args) {
        LinkedHashMap<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                opts.put(key, value);
            }
        }
        return opts;
    }

    static Map<String, String> loadEnv(Path envFile) throws IOException {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int sep = trimmed.indexOf('=');
            if (sep <= 0) {
                continue;
            }
            String value = trimmed.substring(sep + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            env.put(trimmed.substring(0, sep).trim(), value);
        }
        return env;
    }

    static String require(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }

    static String requireEnv(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required env entry " + key);
        }
        return value;
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}

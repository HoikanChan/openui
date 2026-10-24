/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.IncrementalRepairPromptBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Live initial generation and repair through the same public streaming interface used by consumers. Test scope only.
 */
final class SessionBenchRunner {
    private final LlmTransport transport;
    private final String model;
    private final boolean replayInitial;

    SessionBenchRunner(LlmTransport transport, String model) {
        this(transport, model, false);
    }

    SessionBenchRunner(LlmTransport transport, String model, boolean replayInitial) {
        this.transport = transport;
        this.model = model;
        this.replayInitial = replayInitial;
    }

    Map<String, Object> run(RepairCasePack.RepairCase benchCase, Consumer<Map<String, Object>> sink) {
        long started = System.nanoTime();
        List<Map<String, Object>> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        var requestNumber = new java.util.concurrent.atomic.AtomicInteger();
        LlmTransport observed = new LlmTransport() {
            public LlmCall newCall(String body, boolean stream) {
                String kind = body.contains(IncrementalRepairPromptBuilder.INCREMENTAL_REPAIR_MARKER)
                        ? "repair"
                        : body.contains("Answer the user's request in Markdown only.") ? "markdown" : "initial";
                boolean replay = replayInitial && requestNumber.getAndIncrement() == 0;
                requests.add(Map.of("kind", kind, "startedMs", elapsed(started), "stream", stream, "source",
                        replay ? "historical-replay" : "model"));
                if (replay) {
                    com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport historical = new com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport() {
                        public String post(String ignored) {
                            return Json.stringify(
                                    Map.of("choices", List.of(Map.of("message", Map.of("content", benchCase.dsl())))));
                        }
                        public InputStream postStream(String ignored) {
                            String chunk = Json.stringify(
                                    Map.of("choices", List.of(Map.of("delta", Map.of("content", benchCase.dsl())))));
                            return new java.io.ByteArrayInputStream(
                                    ("data: " + chunk + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                        }
                    };
                    return historical.newCall(body, stream);
                }
                return transport.newCall(body, stream);
            }
            public String post(String body) throws com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException {
                return transport.post(body);
            }
            public InputStream postStream(String body)
                    throws com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException {
                return transport.postStream(body);
            }
        };
        var generator = GenUiGenerator.withTransport(GenerationSdk.builder().baseContract(benchCase.contract()).build(),
                GenUiLlmConfig.builder().defaultModel(model).temperature(0).enableThinking(false).build(), observed,
                GenUiValidationConfig.incremental(Integer.MAX_VALUE), null);
        long[] handoffMs = {-1};
        long publicRequestEnteredEpochMs = System.currentTimeMillis();
        long publicRequestEnteredOffsetMs = elapsed(started);
        var generated = generator.generateStream(UiGenerationRequest.builder().userInput(benchCase.originalIntent())
                .response(benchCase.dataModel()).build(), envelope -> {
                    if (RenderStreamEnvelope.TYPE_REPLACE.equals(envelope.type()))
                        handoffMs[0] = elapsed(started);
                    LinkedHashMap<String, Object> event = new LinkedHashMap<>();
                    event.put("type", envelope.type());
                    event.put("seq", envelope.seq());
                    event.put("content", envelope.content());
                    event.put("elapsedMs", elapsed(started));
                    sink.accept(event);
                });
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("type", "result");
        result.put("publicRequestEnteredEpochMs", publicRequestEnteredEpochMs);
        result.put("publicRequestEnteredOffsetMs", publicRequestEnteredOffsetMs);
        result.put("caseId", benchCase.id());
        result.put("generationMode", replayInitial ? "historical-initial-replay" : "live-initial");
        result.put("model", model);
        result.put("temperature", 0);
        result.put("thinking", false);
        result.put("originalIntent", benchCase.originalIntent());
        result.put("dataModel", benchCase.dataModel());
        result.put("contractVersion", benchCase.contract().contractVersion());
        result.put("contractSha256", sha256(Json.stringify(benchCase.contract())));
        result.put("contractHashEncoding", "SHA-256 of SDK Json.stringify(loaded GenerationContract), UTF-8");
        result.put("historicalContractProvenance",
                "unknown unless case contract.json or provenance is supplied; current validity is not historical hallucination proof");
        result.put("validationPassed", generated.endReason() == GenerationEndReason.CHECK_PASSED
                && generated.validationStatus() == ValidationStatus.VALID);
        result.put("qualityCategory", "unconfirmed");
        result.put("accuracyEligible", false);
        result.put("qualityEvidence",
                "Requires human-confirmed intent, full input and expected result plus rendered-page assessment");
        result.put("endReason", generated.endReason() == null ? null : generated.endReason().name());
        result.put("outputDsl", generated.dsl());
        result.put("rounds", generated.rounds().stream().map(round -> {
            LinkedHashMap<String, Object> record = new LinkedHashMap<>();
            record.put("rawOutput", round.rawOutput());
            record.put("currentDefinitions", round.currentDefinitions());
            record.put("durationMs", round.durationMs());
            record.put("endReason", round.endReason());
            record.put("validationStatus", round.validationResult() == null ? null : round.validationResult().status());
            record.put("diagnostics", round.validationResult() == null ? List.of() : round.validationResult().issues());
            return record;
        }).toList());
        result.put("requests", List.copyOf(requests));
        result.put("sessionRequestCount", requests.size());
        result.put("modelRequestCount", requests.stream().filter(r -> "model".equals(r.get("source"))).count());
        result.put("dslRequestCount", requests.stream()
                .filter(r -> "model".equals(r.get("source")) && !"markdown".equals(r.get("kind"))).count());
        result.put("dslSessionMs",
                (handoffMs[0] >= 0 ? handoffMs[0] : elapsed(started)) - publicRequestEnteredOffsetMs);
        result.put("totalIncludingMarkdownMs", elapsed(started) - publicRequestEnteredOffsetMs);
        result.put("sessionBudgetMs", 20000);
        return result;
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

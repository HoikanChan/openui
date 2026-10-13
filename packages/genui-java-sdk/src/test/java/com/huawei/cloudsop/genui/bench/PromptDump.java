/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.repair.ReaskPromptBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Dumps the EXACT reask prompt the current {@link ReaskPromptBuilder} produces for a captured failure case,
 * so a report can quote the real production prompt (not a reconstruction). Reads a corpus case dir with
 * prefix.dsl / invalid.dsl / original-system.txt / original-user.txt.
 *
 * <p>Usage: {@code java ... PromptDump <caseDir> <outFile>}
 */
public final class PromptDump {
    private PromptDump() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String prefix = Files.readString(dir.resolve("prefix.dsl"), StandardCharsets.UTF_8);
        String invalid = Files.readString(dir.resolve("invalid.dsl"), StandardCharsets.UTF_8);
        String sys = Files.readString(dir.resolve("original-system.txt"), StandardCharsets.UTF_8);
        String user = Files.readString(dir.resolve("original-user.txt"), StandardCharsets.UTF_8);

        GenerationSdk sdk = GenerationSdk.create();
        GenerationContract merged = sdk.mergedContract(
                new GenUIPromptRequest(null, null, List.of(), null, null, null, null));

        // Real issues for the withheld statement: validate prefix+invalid, keep the invalid stmt's errors.
        ValidationResult vr = new DefaultOpenuiLangValidator().validate(ValidationRequest.builder()
                .dsl(prefix + "\n" + invalid).contract(merged)
                .rootName(merged.root() == null ? "root" : merged.root())
                .externalRefs(Set.of("data")).mode(ValidationMode.FINAL).build());

        List<ChatMessage> msgs = ReaskPromptBuilder.buildRepairAndContinue(
                user.trim(), prefix, invalid, vr.issues(), merged, sys);

        StringBuilder out = new StringBuilder();
        for (ChatMessage m : msgs) {
            out.append("################ ROLE: ").append(m.role()).append(" (")
                    .append(m.content().length()).append(" chars) ################\n");
            out.append(m.content()).append("\n\n");
        }
        Files.writeString(Path.of(args[1]), out.toString(), StandardCharsets.UTF_8);
        System.out.println("dumped " + msgs.size() + " messages to " + args[1]);
    }
}

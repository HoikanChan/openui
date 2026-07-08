/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
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

/**
 * Offline FINAL-mode validation of a DSL file with the same validator + base contract the streaming gate
 * uses. Prints a JSON verdict to stdout. Used by the reask prompt replay loop as ground truth.
 *
 * <p>Usage: {@code java ... ValidateCli <dsl-file>}
 */
public final class ValidateCli {
    private ValidateCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: ValidateCli <dsl-file>");
            System.exit(2);
        }
        String dsl = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);

        GenerationSdk sdk = GenerationSdk.create();
        GenerationContract merged = sdk.mergedContract(
                new GenUIPromptRequest(null, null, List.of(), null, null, null, null));

        // Mirror the real pipeline: with a data model present, "data" is a known external ref.
        ValidationResult result = new DefaultOpenuiLangValidator().validate(ValidationRequest.builder()
                .dsl(dsl).contract(merged).rootName(merged.root() == null ? "root" : merged.root())
                .externalRefs(java.util.Set.of("data"))
                .mode(ValidationMode.FINAL).build());

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.status().name());
        List<Object> issues = new ArrayList<>();
        for (ValidationIssue issue : result.issues()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("severity", issue.severity().name());
            item.put("code", issue.code());
            item.put("message", issue.message());
            issues.add(item);
        }
        out.put("issues", issues);
        System.out.println(Json.stringify(out));
    }
}

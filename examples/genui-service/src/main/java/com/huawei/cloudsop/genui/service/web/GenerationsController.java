/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.service.api.GenerationsApi;
import com.huawei.cloudsop.genui.service.api.model.ExtensionRegistration;
import com.huawei.cloudsop.genui.service.api.model.GenerationSummary;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.application.GenerationSummaryData;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class GenerationsController implements GenerationsApi {
    private final GenerationAppService appService;

    public GenerationsController(GenerationAppService appService) {
        this.appService = appService;
    }

    @Override
    public ResponseEntity<List<GenerationSummary>> listGenerations() {
        List<GenerationSummary> summaries = new ArrayList<>();
        for (GenerationSummaryData data : appService.listGenerations()) {
            summaries.add(DtoMapper.toDto(data));
        }
        return ResponseEntity.ok(summaries);
    }

    @Override
    public ResponseEntity<GenerationSummary> registerGeneration(String extensionId, ExtensionRegistration body) {
        GenerationSummaryData summary = appService.register(DtoMapper.toGeneration(extensionId, body));
        return ResponseEntity.ok(DtoMapper.toDto(summary));
    }
}

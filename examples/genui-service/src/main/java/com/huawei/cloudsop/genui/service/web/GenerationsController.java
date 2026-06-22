package com.huawei.cloudsop.genui.service.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.cloudsop.genui.service.api.GenerationsApi;
import com.huawei.cloudsop.genui.service.api.model.ExtensionRegistration;
import com.huawei.cloudsop.genui.service.api.model.GenerationSummary;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.application.GenerationSummaryData;

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

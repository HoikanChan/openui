package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.service.api.ContextsApi;
import com.huawei.cloudsop.genui.service.api.model.ContextSummary;
import com.huawei.cloudsop.genui.service.api.model.ExtensionRegistration;
import com.huawei.cloudsop.genui.service.application.ContextSummaryData;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContextsController implements ContextsApi {
  private final GenerationAppService appService;

  public ContextsController(GenerationAppService appService) {
    this.appService = appService;
  }

  @Override
  public ResponseEntity<List<ContextSummary>> listContexts() {
    List<ContextSummary> summaries = new ArrayList<>();
    for (ContextSummaryData data : appService.listContexts()) {
      summaries.add(DtoMapper.toDto(data));
    }
    return ResponseEntity.ok(summaries);
  }

  @Override
  public ResponseEntity<ContextSummary> registerContext(String contextId, ExtensionRegistration body) {
    ContextSummaryData summary = appService.register(DtoMapper.toExtension(contextId, body));
    return ResponseEntity.ok(DtoMapper.toDto(summary));
  }
}

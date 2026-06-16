package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.service.api.PromptsApi;
import com.huawei.cloudsop.genui.service.api.model.AssembleRequest;
import com.huawei.cloudsop.genui.service.api.model.AssembleResult;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromptsController implements PromptsApi {
  private final GenerationAppService appService;

  public PromptsController(GenerationAppService appService) {
    this.appService = appService;
  }

  @Override
  public ResponseEntity<AssembleResult> assemblePrompt(AssembleRequest body) {
    return ResponseEntity.ok(DtoMapper.toDto(appService.assemble(DtoMapper.toPromptRequest(body))));
  }
}

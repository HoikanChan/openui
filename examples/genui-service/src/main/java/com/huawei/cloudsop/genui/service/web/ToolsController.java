package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.service.api.ToolsApi;
import com.huawei.cloudsop.genui.service.tools.SeedToolExecutors;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ToolsController implements ToolsApi {
  private final SeedToolExecutors executors;

  public ToolsController(SeedToolExecutors executors) {
    this.executors = executors;
  }

  @Override
  @SuppressWarnings("unchecked")
  public ResponseEntity<Map<String, Object>> executeTool(String toolName, Object body) {
    Map<String, Object> args = body instanceof Map ? (Map<String, Object>) body : Map.of();
    return ResponseEntity.ok(executors.execute(toolName, args));
  }
}

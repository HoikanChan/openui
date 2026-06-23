package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiGenerationRequestTest {
  @Test
  void builderConstructsRequest() {
    ToolSpec tool = new ToolSpec("load", "Load data", Map.of(), Map.of(), null);
    UiGenerationRequest request =
        UiGenerationRequest.builder()
            .extensionId("extension-a")
            .userInput("show alarms")
            .request(Map.of("title", "Alarms"))
            .response(Map.of("rows", List.of()))
            .suggestion("use table")
            .overlayTools(List.of(tool))
            .overlayRules(List.of("Keep dense"))
            .editMode(true)
            .inlineMode(false)
            .toolCalls(true)
            .bindings(false)
            .build();

    assertEquals("extension-a", request.extensionId());
    assertEquals("show alarms", request.userInput());
    assertEquals("Alarms", request.request().get("title"));
    assertEquals(List.of(), request.response().get("rows"));
    assertEquals("use table", request.suggestion());
    assertEquals(List.of(tool), request.overlayTools());
    assertEquals(List.of("Keep dense"), request.overlayRules());
    assertEquals(true, request.editMode());
    assertEquals(false, request.inlineMode());
    assertEquals(true, request.toolCalls());
    assertEquals(false, request.bindings());
  }

  @Test
  void defaultsCollectionsToEmptyAndLeavesScalarsNull() {
    UiGenerationRequest request = UiGenerationRequest.builder().build();

    assertNull(request.extensionId());
    assertNull(request.userInput());
    assertTrue(request.request().isEmpty());
    assertTrue(request.response().isEmpty());
    assertTrue(request.overlayTools().isEmpty());
    assertTrue(request.overlayRules().isEmpty());
  }

  @Test
  void collectionsAndMapsAreImmutableAndDefensivelyCopied() {
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("count", 1);
    ArrayList<String> rules = new ArrayList<>(List.of("A"));

    UiGenerationRequest request =
        UiGenerationRequest.builder().response(response).overlayRules(rules).build();

    response.put("extra", true);
    rules.add("B");

    assertEquals(Map.of("count", 1), request.response());
    assertEquals(List.of("A"), request.overlayRules());
    assertThrows(UnsupportedOperationException.class, () -> request.response().put("x", 1));
    assertThrows(UnsupportedOperationException.class, () -> request.overlayRules().add("C"));
  }

  @Test
  void preservesRequestAndResponseInsertionOrder() {
    LinkedHashMap<String, Object> requestMap = new LinkedHashMap<>();
    requestMap.put("firstRequest", 1);
    requestMap.put("secondRequest", 2);
    requestMap.put("thirdRequest", 3);
    LinkedHashMap<String, Object> responseMap = new LinkedHashMap<>();
    responseMap.put("firstResponse", 1);
    responseMap.put("secondResponse", 2);
    responseMap.put("thirdResponse", 3);

    UiGenerationRequest request =
        UiGenerationRequest.builder().request(requestMap).response(responseMap).build();

    assertEquals(List.of("firstRequest", "secondRequest", "thirdRequest"), List.copyOf(request.request().keySet()));
    assertEquals(List.of("firstResponse", "secondResponse", "thirdResponse"), List.copyOf(request.response().keySet()));
  }
}

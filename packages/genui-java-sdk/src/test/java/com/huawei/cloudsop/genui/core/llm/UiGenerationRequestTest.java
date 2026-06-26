package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiGenerationRequestTest {
  @Test
  void publicApiDoesNotExposeRequestScopedToolOrRuleFields() {
    List<String> recordComponents =
        Arrays.stream(UiGenerationRequest.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    List<String> builderMethods =
        Arrays.stream(UiGenerationRequest.Builder.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

    assertFalse(recordComponents.contains("overlayTools"));
    assertFalse(recordComponents.contains("overlayRules"));
    assertFalse(builderMethods.contains("overlayTools"));
    assertFalse(builderMethods.contains("overlayRules"));
  }

  @Test
  void builderConstructsRequest() {
    UiGenerationRequest request =
        UiGenerationRequest.builder()
            .extensionId("extension-a")
            .userInput("show alarms")
            .request(Map.of("title", "Alarms"))
            .response(Map.of("rows", List.of()))
            .suggestion("use table")
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
  }

  @Test
  void mapsAreImmutableAndDefensivelyCopied() {
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("count", 1);

    UiGenerationRequest request = UiGenerationRequest.builder().response(response).build();

    response.put("extra", true);

    assertEquals(Map.of("count", 1), request.response());
    assertThrows(UnsupportedOperationException.class, () -> request.response().put("x", 1));
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

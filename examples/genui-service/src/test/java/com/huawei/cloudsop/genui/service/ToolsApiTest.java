package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 工具执行通道:种子工具有 mock 执行器,过滤参数生效,未知工具 404。 */
@SpringBootTest
@AutoConfigureMockMvc
class ToolsApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void queryAlarmsFiltersBySeverity() throws Exception {
    JsonNode result = execute("queryAlarms", "{\"severity\":\"critical\"}");
    assertTrue(result.get("total").asInt() > 0, "demo dataset must contain critical alarms");
    result.get("alarms").forEach(alarm -> assertEquals("critical", alarm.get("severity").asText()));
  }

  @Test
  void queryAlarmsWithoutSeverityReturnsAll() throws Exception {
    JsonNode result = execute("queryAlarms", "{}");
    assertEquals(result.get("alarms").size(), result.get("total").asInt());
    assertTrue(result.get("total").asInt() >= 5);
  }

  @Test
  void acknowledgeAlarmEchoesAlarmId() throws Exception {
    JsonNode result = execute("acknowledgeAlarm", "{\"alarmId\":\"A-001\"}");
    assertTrue(result.get("success").asBoolean());
    assertEquals("A-001", result.get("alarmId").asText());
  }

  @Test
  void unknownToolReturns404() throws Exception {
    mvc.perform(
            post("/v1/tools/noSuchTool/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  private JsonNode execute(String toolName, String args) throws Exception {
    String response =
        mvc.perform(
                post("/v1/tools/" + toolName + "/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(args))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return om.readTree(response);
  }
}

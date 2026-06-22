package com.huawei.bsp.roa.util.restclient;

import java.util.LinkedHashMap;
import java.util.Map;

public class RestfulParametes {
  private String rawData;
  private final LinkedHashMap<String, String> httpContextHeaders = new LinkedHashMap<>();

  public void setRawData(String rawData) {
    this.rawData = rawData;
  }

  public String getRawData() {
    return rawData;
  }

  public void putHttpContextHeader(String name, String value) {
    if (name != null && value != null) {
      httpContextHeaders.put(name, value);
    }
  }

  public Map<String, String> getHttpContextHeaders() {
    return httpContextHeaders;
  }
}

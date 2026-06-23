package com.huawei.bsp.roa.util.restclient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RestfulResponse {
  private int status;
  private String responseContent;
  private InputStream dataStream;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getResponseContent() {
    return responseContent;
  }

  public void setResponseContent(String responseContent) {
    this.responseContent = responseContent;
    this.dataStream =
        new ByteArrayInputStream(
            String.valueOf(responseContent).getBytes(StandardCharsets.UTF_8));
  }

  public InputStream getDataStream() {
    return dataStream;
  }

  public void setDataStream(InputStream dataStream) {
    this.dataStream = dataStream;
  }
}

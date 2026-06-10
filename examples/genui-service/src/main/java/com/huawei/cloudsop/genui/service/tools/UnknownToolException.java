package com.huawei.cloudsop.genui.service.tools;

public class UnknownToolException extends RuntimeException {
  public UnknownToolException(String toolName) {
    super("No executor registered for tool: " + toolName);
  }
}

package com.huawei.cloudsop.genui.service.application;

public class UnknownGenerationException extends RuntimeException {
  public UnknownGenerationException(String extensionId) {
    super("Unknown extensionId: " + extensionId);
  }
}

package com.huawei.cloudsop.genui.service.application;

public class UnknownContextException extends RuntimeException {
  public UnknownContextException(String contextId) {
    super("Unknown contextId: " + contextId);
  }
}

package com.huawei.cloudsop.genui.core;

import java.util.List;

public record ComponentGroup(String name, List<String> components, List<String> notes) {
  public ComponentGroup {
    components = components == null ? List.of() : List.copyOf(components);
    notes = notes == null ? List.of() : List.copyOf(notes);
  }
}

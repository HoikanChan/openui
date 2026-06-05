package dev.openui.genui;

import java.util.List;

public record ComponentGroup(String name, List<String> components, List<String> notes) {
  public ComponentGroup {
    components = components == null ? List.of() : List.copyOf(components);
    notes = notes == null ? List.of() : List.copyOf(notes);
  }
}

package com.huawei.cloudsop.genui.core.validation.type;

/** Result of resolving one generated DSL path against the concrete Render Data Model. */
public sealed interface DataPathResolution permits DataPathResolution.Resolved, DataPathResolution.Missing,
        DataPathResolution.InvalidTraversal, DataPathResolution.Unprovable {

    String path();

    record Evidence(int present, int missing, int nulls, int total) {
    }

    record Resolved(String path, ValueType type, Evidence evidence) implements DataPathResolution {
    }

    record Missing(String path, String hint) implements DataPathResolution {
    }

    record InvalidTraversal(String path, String actualType, String hint) implements DataPathResolution {
    }

    record Unprovable(String path, String hint) implements DataPathResolution {
    }
}

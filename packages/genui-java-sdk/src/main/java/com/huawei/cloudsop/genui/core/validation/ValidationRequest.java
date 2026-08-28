package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Input to the DSL validator.
 *
 * <p>Use {@link #builder()} to construct instances. The {@code externalRefs} set is always
 * non-null and immutable; pass {@code null} to get an empty set. A {@code null} {@code dataModel}
 * means the caller did not supply runtime data, while an empty map is a concrete empty model.
 */
public record ValidationRequest(
    String dsl,
    /** Contract to validate against; {@code null} for syntax-only validation. */
    GenerationContract contract,
    /** Expected root component name, or {@code null}. */
    String rootName,
    /** External type names referenced in the DSL; never {@code null}. */
    Set<String> externalRefs,
    /** Concrete Render Data Model used to validate generated data paths; {@code null} if absent. */
    Map<String, Object> dataModel,
    ValidationMode mode,
    /** Caller-supplied correlation id for logging, or {@code null}. */
    String requestId) {

  public ValidationRequest {
    externalRefs =
        externalRefs == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(externalRefs));
    if (dataModel != null) {
      dataModel = Collections.unmodifiableMap(new LinkedHashMap<>(dataModel));
    }
  }

  /** Source-compatible constructor for callers compiled against the pre-data-model request. */
  public ValidationRequest(
      String dsl,
      GenerationContract contract,
      String rootName,
      Set<String> externalRefs,
      ValidationMode mode,
      String requestId) {
    this(dsl, contract, rootName, externalRefs, null, mode, requestId);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String dsl;
    private GenerationContract contract;
    private String rootName;
    private Set<String> externalRefs;
    private Map<String, Object> dataModel;
    private ValidationMode mode;
    private String requestId;

    private Builder() {}

    public Builder dsl(String dsl) {
      this.dsl = dsl;
      return this;
    }

    public Builder contract(GenerationContract contract) {
      this.contract = contract;
      return this;
    }

    public Builder rootName(String rootName) {
      this.rootName = rootName;
      return this;
    }

    public Builder externalRefs(Set<String> externalRefs) {
      this.externalRefs = externalRefs;
      return this;
    }

    public Builder dataModel(Map<String, Object> dataModel) {
      this.dataModel = dataModel;
      return this;
    }

    public Builder mode(ValidationMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder requestId(String requestId) {
      this.requestId = requestId;
      return this;
    }

    public ValidationRequest build() {
      Objects.requireNonNull(dsl, "dsl");
      Objects.requireNonNull(mode, "mode");
      return new ValidationRequest(dsl, contract, rootName, externalRefs, dataModel, mode, requestId);
    }
  }
}

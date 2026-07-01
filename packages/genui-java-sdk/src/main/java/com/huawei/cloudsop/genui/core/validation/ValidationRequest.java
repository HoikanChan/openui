package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Input to the DSL validator.
 *
 * <p>Use {@link #builder()} to construct instances. The {@code externalRefs} set is always
 * non-null and immutable; pass {@code null} to get an empty set.
 */
public record ValidationRequest(
    String dsl,
    /** Contract to validate against; {@code null} for syntax-only validation. */
    GenerationContract contract,
    /** Expected root component name, or {@code null}. */
    String rootName,
    /** External type names referenced in the DSL; never {@code null}. */
    Set<String> externalRefs,
    ValidationMode mode,
    /** Caller-supplied correlation id for logging, or {@code null}. */
    String requestId) {

  public ValidationRequest {
    externalRefs =
        externalRefs == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(externalRefs));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String dsl;
    private GenerationContract contract;
    private String rootName;
    private Set<String> externalRefs;
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
      return new ValidationRequest(dsl, contract, rootName, externalRefs, mode, requestId);
    }
  }
}

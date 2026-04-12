package dev.openui.langcore.runtime;

import java.util.List;

/**
 * The result of evaluating an {@code Action([...])} call — a list of
 * {@link ActionStep}s for the framework adapter to execute.
 *
 * Ref: Req 10 AC13
 */
public record ActionPlan(List<ActionStep> steps) {}

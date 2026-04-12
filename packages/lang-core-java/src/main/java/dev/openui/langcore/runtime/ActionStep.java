package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.Node;

import java.util.List;

/**
 * Discriminated union of action steps produced by Action-builtin evaluation.
 *
 * <p>Framework adapters inspect the concrete sub-type and execute the
 * appropriate side-effect (query run, state set, navigation, etc.).
 *
 * Ref: Req 10 AC13-18, Design §18
 */
public sealed interface ActionStep
        permits ActionStep.RunStep, ActionStep.SetStep, ActionStep.ResetStep,
                ActionStep.ToAssistantStep, ActionStep.OpenUrlStep {

    /** Execute a Query or Mutation by statement ID. Ref: Req 10 AC14 */
    record RunStep(String statementId, String refType) implements ActionStep {}

    /** Set a {@code $state} variable to the result of evaluating {@code valueAST}. Ref: Req 10 AC17 */
    record SetStep(String target, Node valueAST) implements ActionStep {}

    /** Reset one or more {@code $state} variables to their default values. Ref: Req 10 AC18 */
    record ResetStep(List<String> targets) implements ActionStep {}

    /** Send a message back to the assistant conversation. Ref: Req 10 AC15 */
    record ToAssistantStep(String message, String context) implements ActionStep {}

    /** Navigate to a URL. Ref: Req 10 AC16 */
    record OpenUrlStep(String url) implements ActionStep {}
}

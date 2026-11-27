// define library
export { createLibrary, defineComponent } from "./library";
export type {
  ComponentGroup,
  ComponentPropsSchema,
  ComponentPropsSchemaProperty,
  ComponentRenderProps,
  ComponentRenderer,
  DefinedComponent,
  GenerationContract,
  Library,
  LibraryDefinition,
  LibraryExtensionDefinition,
  PromptOptions,
  SubComponentOf,
  ToolDescriptor,
} from "./library";

// openui-lang renderer
export { Renderer } from "./Renderer";
export type { RendererProps } from "./Renderer";

// openui-lang action types
export { ACTION_STEPS, BuiltinActionType, isElementNode } from "@cloudsop/openui-lang-core";
export type {
  ActionEvent,
  ActionPlan,
  ActionStep,
  ElementNode,
  OpenUIError,
  ParseResult,
} from "@cloudsop/openui-lang-core";

// openui-lang parser (server-side use)
export { createParser, createStreamingParser } from "@cloudsop/openui-lang-core";

// Standalone prompt generation (no Zod deps — usable on backend)
export { generatePrompt, getBuiltinsManifest } from "@cloudsop/openui-lang-core";
export type {
  BuiltinManifestEntry,
  ComponentPromptSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "@cloudsop/openui-lang-core";

// openui-lang edit/merge
export { mergeStatements } from "@cloudsop/openui-lang-core";

// renderElementNode for canvas rendering
export { renderElementNode } from "./renderElementNode";

// openui-lang context hooks (for use inside component renderers)
export {
  FormNameContext,
  useFormName,
  useGetFieldValue,
  useIsStreaming,
  useRenderNode,
  useSetDefaultValue,
  useSetFieldValue,
  useTriggerAction,
} from "./context";

// Runtime — reactive bindings, store, evaluator, query manager, field binding
export { ToolNotFoundError, extractToolResult, isReactiveAssign } from "@cloudsop/openui-lang-core";
export type {
  EvaluationContext,
  McpClientLike,
  ReactiveAssign,
  StateField,
  ToolProvider,
} from "@cloudsop/openui-lang-core";
export { reactive } from "./runtime";

// Unified field state hook — component authors use this
export { useStateField } from "./hooks/useStateField";

// openui-lang form validation
export {
  FormValidationContext,
  useCreateFormValidation,
  useFormValidation,
} from "./hooks/useFormValidation";
export type { FormValidationContextValue } from "./hooks/useFormValidation";

export {
  builtInValidators,
  parseRules,
  parseStructuredRules,
  validate,
} from "@cloudsop/openui-lang-core";
export type { ParsedRule, ValidatorFn } from "@cloudsop/openui-lang-core";

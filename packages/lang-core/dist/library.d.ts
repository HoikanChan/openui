// Hand-crafted declaration stub for lang-core/dist/library.ts
// Satisfies the "import from ./library.ts" in the tsdown-built index.d.mts
// without requiring the full source tree to be present.

import { z } from "zod";

export interface LibraryJSONSchema {
  [key: string]: unknown;
}

export type SubComponentOf<P> = {
  type: "element";
  typeName: string;
  props: P;
  partial: boolean;
};

export interface ComponentRenderProps<P = Record<string, unknown>, RenderNode = unknown> {
  props: P;
  renderNode: (value: unknown) => RenderNode;
}

export interface DefinedComponent<T extends z.ZodObject<any> = z.ZodObject<any>, C = unknown> {
  name: string;
  props: T;
  description: string;
  component: C;
  ref: z.ZodType<SubComponentOf<z.infer<T>>>;
}

export interface ComponentGroup {
  name: string;
  components: string[];
  notes?: string[];
}

export type ToolDescriptor = string | { name: string; description?: string; parameters?: unknown };

export interface PromptOptions {
  preamble?: string;
  additionalRules?: string[];
  examples?: string[];
  toolExamples?: string[];
  tools?: ToolDescriptor[];
  editMode?: boolean;
  inlineMode?: boolean;
  toolCalls?: boolean;
  bindings?: boolean;
}

export interface Library<C = unknown> {
  readonly components: Record<string, DefinedComponent<any, C>>;
  readonly componentGroups: ComponentGroup[] | undefined;
  readonly root: string | undefined;
  prompt(options?: PromptOptions): string;
  toSpec(): unknown;
  toJSONSchema(): LibraryJSONSchema;
}

export interface LibraryDefinition<C = unknown> {
  components: DefinedComponent<any, C>[];
  componentGroups?: ComponentGroup[];
  root?: string;
}

export declare function defineComponent<T extends z.ZodObject<any>, C>(config: {
  name: string;
  props: T;
  description: string;
  component: C;
}): DefinedComponent<T, C>;

export declare function createLibrary<C = unknown>(input: LibraryDefinition<C>): Library<C>;

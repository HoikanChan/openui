import { z } from "zod";
import type { ComponentPromptSpec, DataModelSpec, PromptSpec, ToolSpec } from "./parser/prompt";
import { generatePrompt } from "./parser/prompt";
import type { LibraryJSONSchema } from "./parser/types";
import { isReactiveSchema } from "./reactive";

export type { LibraryJSONSchema } from "./parser/types";

// ─── Sub-component type ──────────────────────────────────────────────────────

/**
 * Runtime shape of a parsed sub-component element as seen by parent renderers.
 */
export type SubComponentOf<P> = {
  type: "element";
  typeName: string;
  props: P;
  partial: boolean;
};

// ─── Renderer types (framework-generic) ──────────────────────────────────────

/**
 * The props passed to every component renderer.
 *
 * Framework adapters narrow `RenderNode`:
 * - React:  RenderNode = ReactNode
 * - Svelte: RenderNode = Snippet<[unknown]>
 * - Vue:    RenderNode = VNode
 */
export interface ComponentRenderProps<P = Record<string, unknown>, RenderNode = unknown> {
  props: P;
  renderNode: (value: unknown) => RenderNode;
}

// ─── DefinedComponent (framework-generic) ────────────────────────────────────

/**
 * A fully defined component. The `C` parameter represents the
 * framework-specific component/renderer type. lang-core never
 * inspects this value — it is stored opaquely and consumed
 * by the framework adapter's Renderer.
 */
export interface DefinedComponent<T extends z.ZodObject<any> = z.ZodObject<any>, C = unknown> {
  name: string;
  props: T;
  description: string;
  component: C;
  /** Use in parent schemas: `z.array(ChildComponent.ref)` */
  ref: z.ZodType<SubComponentOf<z.infer<T>>>;
}

/**
 * Define a component with name, schema, description, and renderer.
 * Registers the Zod schema globally and returns a `.ref` for parent schemas.
 */
export function defineComponent<T extends z.ZodObject<any>, C>(config: {
  name: string;
  props: T;
  description: string;
  component: C;
}): DefinedComponent<T, C> {
  (config.props as any).register(z.globalRegistry, { id: config.name });
  return {
    ...config,
    ref: config.props as unknown as z.ZodType<SubComponentOf<z.infer<T>>>,
  };
}

// ─── Groups & Prompt Options ─────────────────────────────────────────────────

export interface ComponentGroup {
  name: string;
  components: string[];
  notes?: string[];
}

/** Tool descriptor for prompt generation — simple string or rich ToolSpec. */
export type ToolDescriptor = string | ToolSpec;

export interface PromptOptions {
  preamble?: string;
  additionalRules?: string[];
  /** Examples shown when no tools are present (static/layout patterns). */
  examples?: string[];
  /** Examples shown when tools ARE present (Query/Mutation patterns). Takes priority over `examples`. */
  toolExamples?: string[];
  /** Available tools for Query() — string names or rich ToolSpec descriptors injected into the prompt. */
  tools?: ToolDescriptor[];
  /** Enable edit-mode instructions in the prompt. */
  editMode?: boolean;
  /** Enable inline mode — LLM can respond with text + optional openui-lang fenced code. */
  inlineMode?: boolean;
  /** Enable Query(), Mutation(), @Run, tool workflow. Default: true if tools provided. */
  toolCalls?: boolean;
  /** Enable $variables, @Set, @Reset, interactive filters. Default: true if toolCalls. */
  bindings?: boolean;
  /** Pass host data into the prompt as a fenced JSON block under ## Data Model. */
  dataModel?: DataModelSpec;
}

export interface GenerationContract extends PromptSpec {
  contractVersion: string;
  components: Record<string, ComponentPromptSpec>;
  componentGroups?: ComponentGroup[];
  tools?: ToolDescriptor[];
  examples?: string[];
  additionalRules?: string[];
}

export interface LibraryExtensionDefinition<C = unknown> {
  components?: DefinedComponent<any, C>[];
  componentGroups?: ComponentGroup[];
  tools?: ToolDescriptor[];
  examples?: string[];
  additionalRules?: string[];
  contractVersion?: string;
}

// ─── Zod introspection ──────────────────────────────────────────────────────

function getZodDef(schema: unknown): any {
  return (schema as any)?._zod?.def;
}

function getZodType(schema: unknown): string | undefined {
  return getZodDef(schema)?.type;
}

function isOptionalType(schema: unknown): boolean {
  const type = getZodType(schema);
  return type === "optional" || type === "default" || type === "nullable";
}

function unwrap(schema: unknown): unknown {
  let s = schema;
  let def = getZodDef(s);
  while (def?.type === "optional" || def?.type === "default" || def?.type === "nullable") {
    s = def.innerType;
    def = getZodDef(s);
  }
  return s;
}

function isArrayType(schema: unknown): boolean {
  const s = unwrap(schema);
  return getZodType(s) === "array";
}

function getArrayInnerType(schema: unknown): unknown | undefined {
  const s = unwrap(schema);
  const def = getZodDef(s);
  if (def?.type === "array") return def.element ?? def.innerType;
  return undefined;
}

function getEnumValues(schema: unknown): string[] | undefined {
  const s = unwrap(schema);
  const def = getZodDef(s);
  if (def?.type !== "enum") return undefined;
  if (Array.isArray(def.values)) return def.values;
  if (def.entries && typeof def.entries === "object") return Object.keys(def.entries);
  return undefined;
}

function getSchemaId(schema: unknown): string | undefined {
  try {
    const meta = z.globalRegistry.get(schema as z.ZodType);
    return meta?.id;
  } catch {
    return undefined;
  }
}

function getUnionOptions(schema: unknown): unknown[] | undefined {
  const def = getZodDef(schema);
  if (def?.type === "union" && Array.isArray(def.options)) return def.options;
  return undefined;
}

function getObjectShape(schema: unknown): Record<string, unknown> | undefined {
  const def = getZodDef(schema);
  if (def?.type === "object" && def.shape && typeof def.shape === "object")
    return def.shape as Record<string, unknown>;
  return undefined;
}

/**
 * Resolve the type annotation for a schema field.
 * Returns a human-readable type string for the schema.
 * If the schema is marked reactive(), prefixes with "$binding<...>".
 */
function resolveTypeAnnotation(schema: unknown): string | undefined {
  const isReactive = isReactiveSchema(schema);
  const inner = unwrap(schema);

  const baseType = resolveBaseType(inner);
  if (!baseType) return undefined;
  return isReactive ? `$binding<${baseType}>` : baseType;
}

function resolveBaseType(inner: unknown): string | undefined {
  const directId = getSchemaId(inner);
  if (directId) return directId;

  const unionOpts = getUnionOptions(inner);
  if (unionOpts) {
    const resolved = unionOpts.map((o) => resolveTypeAnnotation(o));
    const names = resolved.filter(Boolean) as string[];
    if (names.length > 0) return names.join(" | ");
  }

  if (isArrayType(inner)) {
    const arrayInner = getArrayInnerType(inner);
    if (!arrayInner) return undefined;
    const innerType = resolveTypeAnnotation(arrayInner);
    if (innerType) {
      const isUnion = getUnionOptions(unwrap(arrayInner)) !== undefined;
      return isUnion ? `(${innerType})[]` : `${innerType}[]`;
    }
    return undefined;
  }

  const zodType = getZodType(inner);
  if (zodType === "string") return "string";
  if (zodType === "number") return "number";
  if (zodType === "boolean") return "boolean";
  if (zodType === "any") return "any";

  if (zodType === "record") {
    const def = getZodDef(inner);
    const keyType = resolveTypeAnnotation(def?.keyType) ?? "string";
    const valueType = resolveTypeAnnotation(def?.valueType) ?? "any";
    return `Record<${keyType}, ${valueType}>`;
  }

  const enumVals = getEnumValues(inner);
  if (enumVals) return enumVals.map((v) => `"${v}"`).join(" | ");

  if (zodType === "literal") {
    const vals = getZodDef(inner)?.values;
    if (Array.isArray(vals) && vals.length === 1) {
      const v = vals[0];
      return typeof v === "string" ? `"${v}"` : String(v);
    }
  }

  const shape = getObjectShape(inner);
  if (shape) {
    const fields = Object.entries(shape).map(([name, fieldSchema]) => {
      const opt = isOptionalType(fieldSchema) ? "?" : "";
      const fieldType = resolveTypeAnnotation(fieldSchema as z.ZodType);
      return fieldType ? `${name}${opt}: ${fieldType}` : `${name}${opt}`;
    });
    return `{${fields.join(", ")}}`;
  }

  // Fallback for unrecognized Zod types (z.tuple, z.date, etc.)
  // "any" is safer than undefined — the LLM sees `param: any` instead of bare `param`
  return "any";
}

// ─── Field analysis & signature generation ──────────────────────────────────

interface FieldInfo {
  name: string;
  isOptional: boolean;
  isArray: boolean;
  typeAnnotation?: string;
}

function analyzeFields(shape: Record<string, z.ZodType>): FieldInfo[] {
  return Object.entries(shape).map(([name, schema]) => ({
    name,
    isOptional: isOptionalType(schema),
    isArray: isArrayType(schema),
    typeAnnotation: resolveTypeAnnotation(schema),
  }));
}

function buildSignature(componentName: string, fields: FieldInfo[]): string {
  const params = fields.map((f) => {
    if (f.typeAnnotation) {
      return f.isOptional ? `${f.name}?: ${f.typeAnnotation}` : `${f.name}: ${f.typeAnnotation}`;
    }
    if (f.isArray) {
      return f.isOptional ? `[${f.name}]?` : `[${f.name}]`;
    }
    return f.isOptional ? `${f.name}?` : f.name;
  });
  return `${componentName}(${params.join(", ")})`;
}

function buildComponentSpecs(
  components: Record<string, DefinedComponent<any, any>>,
): Record<string, ComponentPromptSpec> {
  const specs: Record<string, ComponentPromptSpec> = {};
  for (const [name, def] of Object.entries(components)) {
    const fields = analyzeFields(def.props.shape);
    specs[name] = {
      signature: buildSignature(name, fields),
      description: def.description,
    };
  }
  return specs;
}

// ─── Library ────────────────────────────────────────────────────────────────

export interface Library<C = unknown> {
  readonly components: Record<string, DefinedComponent<any, C>>;
  readonly componentGroups: ComponentGroup[] | undefined;
  readonly root: string | undefined;
  readonly contractVersion: string;

  prompt(options?: PromptOptions): string;
  toSpec(): GenerationContract;
  toJSONSchema(): LibraryJSONSchema;
  extend(extension: LibraryExtensionDefinition<C>): Library<C>;
}

export interface LibraryDefinition<C = unknown> extends LibraryExtensionDefinition<C> {
  components: DefinedComponent<any, C>[];
  root?: string;
}

const DEFAULT_GENERATION_CONTRACT_VERSION = "1.0.0";

function cloneComponents<C>(components: DefinedComponent<any, C>[]): DefinedComponent<any, C>[] {
  return [...components];
}

function cloneGroups(groups: ComponentGroup[] | undefined): ComponentGroup[] | undefined {
  return groups?.map((group) => ({
    ...group,
    components: [...group.components],
    notes: group.notes ? [...group.notes] : undefined,
  }));
}

function cloneTools(tools: ToolDescriptor[] | undefined): ToolDescriptor[] | undefined {
  return tools ? [...tools] : undefined;
}

function cloneStrings(values: string[] | undefined): string[] | undefined {
  return values ? [...values] : undefined;
}

function assertUniqueComponentNames<C>(components: DefinedComponent<any, C>[], scope: string): void {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const component of components) {
    if (seen.has(component.name)) duplicates.add(component.name);
    seen.add(component.name);
  }
  if (duplicates.size > 0) {
    throw new Error(
      `[${scope}] Component name collision: ${Array.from(duplicates).sort().join(", ")}`,
    );
  }
}

function buildComponentsRecord<C>(
  components: DefinedComponent<any, C>[],
): Record<string, DefinedComponent<any, C>> {
  const componentsRecord: Record<string, DefinedComponent<any, C>> = {};
  for (const comp of components) {
    if (!z.globalRegistry.has(comp.props)) {
      comp.props.register(z.globalRegistry, { id: comp.name });
    }
    componentsRecord[comp.name] = comp;
  }
  return componentsRecord;
}

function validateRoot<C>(
  root: string | undefined,
  componentsRecord: Record<string, DefinedComponent<any, C>>,
): void {
  if (!root || componentsRecord[root]) return;
  const available = Object.keys(componentsRecord).join(", ");
  throw new Error(
    `[createLibrary] Root component "${root}" was not found in components. Available components: ${available}`,
  );
}

function validateComponentGroups<C>(
  groups: ComponentGroup[] | undefined,
  componentsRecord: Record<string, DefinedComponent<any, C>>,
  scope: string,
): void {
  if (!groups?.length) return;

  const missing = new Set<string>();
  for (const group of groups) {
    for (const componentName of group.components) {
      if (!componentsRecord[componentName]) missing.add(componentName);
    }
  }

  if (missing.size > 0) {
    throw new Error(
      `[${scope}] Component group references missing component(s): ${Array.from(missing)
        .sort()
        .join(", ")}`,
    );
  }
}

function mergeGroups(
  baseGroups: ComponentGroup[] | undefined,
  extensionGroups: ComponentGroup[] | undefined,
): ComponentGroup[] | undefined {
  const merged = [...(cloneGroups(baseGroups) ?? []), ...(cloneGroups(extensionGroups) ?? [])];
  return merged.length ? merged : undefined;
}

/**
 * Create a component library from an array of defined components.
 */
export function createLibrary<C = unknown>(input: LibraryDefinition<C>): Library<C> {
  const components = cloneComponents(input.components);
  const componentGroups = cloneGroups(input.componentGroups);
  const tools = cloneTools(input.tools) ?? [];
  const examples = cloneStrings(input.examples) ?? [];
  const additionalRules = cloneStrings(input.additionalRules) ?? [];
  const contractVersion = input.contractVersion ?? DEFAULT_GENERATION_CONTRACT_VERSION;

  assertUniqueComponentNames(components, "createLibrary");
  const componentsRecord = buildComponentsRecord(components);
  validateRoot(input.root, componentsRecord);
  validateComponentGroups(componentGroups, componentsRecord, "createLibrary");

  const library: Library<C> = {
    components: componentsRecord,
    componentGroups,
    root: input.root,
    contractVersion,

    prompt(options?: PromptOptions): string {
      const mergedTools = [...tools, ...(options?.tools ?? [])];
      const mergedExamples = [...examples, ...(options?.examples ?? [])];
      const mergedAdditionalRules = [...additionalRules, ...(options?.additionalRules ?? [])];
      const spec: PromptSpec = {
        root: input.root,
        components: buildComponentSpecs(componentsRecord),
        componentGroups,
        ...options,
        tools: mergedTools.length ? mergedTools : options?.tools,
        examples: mergedExamples.length ? mergedExamples : options?.examples,
        additionalRules: mergedAdditionalRules.length
          ? mergedAdditionalRules
          : options?.additionalRules,
      };
      return generatePrompt(spec);
    },

    toSpec(): GenerationContract {
      return {
        contractVersion,
        root: input.root,
        components: buildComponentSpecs(componentsRecord),
        componentGroups: cloneGroups(componentGroups),
        tools: cloneTools(tools) ?? [],
        examples: cloneStrings(examples) ?? [],
        additionalRules: cloneStrings(additionalRules) ?? [],
      };
    },

    toJSONSchema(): LibraryJSONSchema {
      const combinedSchema = z.object(
        Object.fromEntries(Object.entries(componentsRecord).map(([k, v]) => [k, v.props])) as any,
      );
      return z.toJSONSchema(combinedSchema);
    },

    extend(extension: LibraryExtensionDefinition<C>): Library<C> {
      const extensionComponents = cloneComponents(extension.components ?? []);
      assertUniqueComponentNames(extensionComponents, "Library.extend");

      const baseNames = new Set(Object.keys(componentsRecord));
      const collisions = extensionComponents
        .map((component) => component.name)
        .filter((name) => baseNames.has(name));
      if (collisions.length > 0) {
        throw new Error(
          `[Library.extend] Component name collision: ${Array.from(new Set(collisions))
            .sort()
            .join(", ")}`,
        );
      }

      return createLibrary<C>({
        components: [...components, ...extensionComponents],
        componentGroups: mergeGroups(componentGroups, extension.componentGroups),
        root: input.root,
        contractVersion: extension.contractVersion ?? contractVersion,
        tools: [...tools, ...(extension.tools ?? [])],
        examples: [...examples, ...(extension.examples ?? [])],
        additionalRules: [...additionalRules, ...(extension.additionalRules ?? [])],
      });
    },
  };

  return library;
}

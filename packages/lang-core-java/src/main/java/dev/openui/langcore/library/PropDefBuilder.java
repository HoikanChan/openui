package dev.openui.langcore.library;

/**
 * Fluent builder for a single {@link PropDef}, obtained via
 * {@link ComponentDefBuilder#prop(String)}.
 *
 * <p>Call {@link #add()} when done to return to the parent
 * {@link ComponentDefBuilder}.
 *
 * Ref: Design §13
 */
public final class PropDefBuilder {

    private final ComponentDefBuilder parent;
    private final String name;

    private boolean required     = false;
    private Object  defaultValue = null;
    private String  typeAnnotation = "any";
    private boolean isArray      = false;
    private boolean isReactive   = false;

    PropDefBuilder(ComponentDefBuilder parent, String name) {
        this.parent = parent;
        this.name   = name;
    }

    /** Set the type annotation string (e.g. {@code "string"}, {@code "Card[]"}, {@code "$binding<string>"}). */
    public PropDefBuilder type(String typeAnnotation) {
        this.typeAnnotation = typeAnnotation;
        this.isArray        = typeAnnotation != null && typeAnnotation.endsWith("[]");
        this.isReactive     = typeAnnotation != null && typeAnnotation.startsWith("$binding");
        return this;
    }

    /** Mark this prop as required (no default). */
    public PropDefBuilder required() {
        this.required = true;
        return this;
    }

    /** Mark this prop as optional (not required). */
    public PropDefBuilder optional() {
        this.required = false;
        return this;
    }

    /** Set the default value used when the argument is omitted. */
    public PropDefBuilder defaultValue(Object value) {
        this.defaultValue = value;
        return this;
    }

    /** Mark this prop as reactive (accepts a {@code $variable} binding). */
    public PropDefBuilder reactive() {
        this.isReactive = true;
        return this;
    }

    /**
     * Finish this prop and return to the parent {@link ComponentDefBuilder}.
     */
    public ComponentDefBuilder add() {
        PropDef def = new PropDef(name, required, defaultValue, typeAnnotation, isArray, isReactive);
        parent.addProp(def);
        return parent;
    }
}

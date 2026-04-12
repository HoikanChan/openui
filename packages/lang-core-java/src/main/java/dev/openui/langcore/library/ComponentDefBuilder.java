package dev.openui.langcore.library;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for a single {@link ComponentDef}, obtained via
 * {@link Libraries#defineComponent(String)}.
 *
 * <p>Call {@link #build()} when done to produce the final {@link ComponentDef}.
 *
 * Ref: Design §13
 */
public final class ComponentDefBuilder {

    private final String name;
    private String description = null;
    private Object component   = null;
    private final List<PropDef> props = new ArrayList<>();

    ComponentDefBuilder(String name) {
        this.name = name;
    }

    /** Set a human-readable description for this component. */
    public ComponentDefBuilder description(String description) {
        this.description = description;
        return this;
    }

    /** Begin building a prop with the given name. */
    public PropDefBuilder prop(String propName) {
        return new PropDefBuilder(this, propName);
    }

    /** Package-visible — called by {@link PropDefBuilder#add()}. */
    void addProp(PropDef def) {
        props.add(def);
    }

    /** Attach the actual component object (e.g. a UI component reference). */
    public ComponentDefBuilder component(Object component) {
        this.component = component;
        return this;
    }

    /** Build and return the final {@link ComponentDef}. */
    public ComponentDef build() {
        return new ComponentDef(name, List.copyOf(props), description, component);
    }
}

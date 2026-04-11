package dev.openui.langcore.parser.stmt;

// Used in merge/patch operations to represent a deletion (e.g. patch `name = null`)
public record NullStatement(String id) implements Statement {}

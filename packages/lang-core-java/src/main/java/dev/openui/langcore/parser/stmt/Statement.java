package dev.openui.langcore.parser.stmt;

public sealed interface Statement permits
    ValueStatement, StateStatement, QueryStatement, MutationStatement, NullStatement {}

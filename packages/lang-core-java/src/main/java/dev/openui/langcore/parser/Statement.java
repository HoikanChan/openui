package dev.openui.langcore.parser;

public sealed interface Statement permits
    ValueStatement, StateStatement, QueryStatement, MutationStatement, NullStatement {}

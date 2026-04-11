package dev.openui.langcore.parser.ast;

public sealed interface Node permits
    LiteralNode, RefNode, StateRefNode, RuntimeRefNode,
    AssignNode, BinaryNode, UnaryNode, MemberNode,
    TernaryNode, CallNode, BuiltinCallNode,
    ElementNode, ArrayNode, ObjectNode {}

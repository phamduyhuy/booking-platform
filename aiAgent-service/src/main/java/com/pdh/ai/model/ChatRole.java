package com.pdh.ai.model;

/**
 * Roles persisted for chat memory entries. Mirrors LangChain4j ChatMessageType values
 * so we can safely convert between database entities and LangChain4j chat messages.
 */
public enum ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

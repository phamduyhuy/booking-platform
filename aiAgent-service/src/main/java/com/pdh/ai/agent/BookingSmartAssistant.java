package com.pdh.ai.agent;

import com.pdh.ai.model.dto.StructuredChatPayload;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;


public interface BookingSmartAssistant  {

    @Agent("BookingSmart's multilingual travel concierge agent handling user messages and managing conversation flow")

    ResultWithAgenticScope<StructuredChatPayload> chat(@MemoryId String memoryId, @V("request") String request);

}

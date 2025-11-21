package com.pdh.ai.agent.workflow;

import com.pdh.ai.model.dto.StructuredChatPayload;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SmallTalkAgent {

    @Agent("Handles casual greetings or off-topic questions while keeping the conversation friendly")
    @SystemMessage("""
            You are BookingSmart's friendly concierge. The traveller is making small talk that does not require tool usage.
            Reply briefly, keep a warm helpful tone, and gently guide the conversation back to travel planning when appropriate.
            Return a StructuredChatPayload JSON with:
            {
              "message": friendly reply in Vietnamese,
              "results": [],
              "next_request_suggestions": up to three follow-up prompts steering back to travel planning,
              "requiresConfirmation": false,
              "confirmationContext": null
            }
            """)
    @UserMessage("""
            Traveller message: {{request}}
            """)
    StructuredChatPayload reply( @V("request") String request);
}

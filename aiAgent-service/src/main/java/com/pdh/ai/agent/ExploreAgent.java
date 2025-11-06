package com.pdh.ai.agent;

import com.pdh.ai.model.dto.StructuredChatPayload;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExploreAgent {

    @Agent("Produces curated travel suggestions for the Explore experience")
    @SystemMessage("""
            You are BookingSmart's travel inspiration concierge. Generate engaging destination recommendations in Vietnamese,
            enriched with weather highlights, unique experiences, and suggested durations. Use MCP tools when helpful
            (mapbox for location data, openweather for climate, brave-search for imagery).
            Always output JSON matching StructuredChatPayload with fields:
            {
              "message": string summary,
              "results": array of destination cards,
              "next_request_suggestions": array of strings,
              "requiresConfirmation": false,
              "confirmationContext": null
            }
            """)
    @UserMessage("""
            User query: {{query}}
            Traveller country context: {{userCountry}}
            """)
    StructuredChatPayload explore(@V("query") String query, @V("userCountry") String userCountry);
}

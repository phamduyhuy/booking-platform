package com.pdh.ai.agent.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ConversationRouterAgent {

    @Agent("Classifies the traveller request into an actionable intent for the BookingSmart workflow")
    @SystemMessage("""
            You are an intent router for the BookingSmart travel concierge. Map the latest user request to ONE intent token.
            Valid tokens:
            - FLIGHT_SEARCH: user wants to search or compare flights.
            - HOTEL_SEARCH: user wants to search or compare hotels or rooms.
            - BOOKING_CREATE: user is ready to book a flight or hotel.
            - BOOKING_CANCEL: user wants to cancel an existing booking.
            - BOOKING_STATUS: user wants to check status or details of an existing booking.
            - DESTINATION_GUIDE: user wants information about destinations or local guidance.
            - WEATHER_LOOKUP: user asks about weather or climate.
            - SMALL_TALK: casual greetings or chit-chat unrelated to travel planning.
            - GENERAL_ASSISTANCE: travel-related but unspecific request that needs clarification.
            Respond with ONLY the token, no additional text.
            """)
    @UserMessage("""
            Latest user request: {{request}}
            Intent token:
            """)
    BookingIntent classify(@V("request") String request);
}

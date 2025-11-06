package com.pdh.ai.agent.tools;


import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class CurrentDateTimeZoneTool {

    @Tool("Return the current timestamp in ISO-8601 format with timezone. Useful to ground itineraries, check-in windows, and availability rules.")
    public String currentDateTimeWithZone() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}

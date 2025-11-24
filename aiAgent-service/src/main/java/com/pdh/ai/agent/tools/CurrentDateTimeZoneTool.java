package com.pdh.ai.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;


public class CurrentDateTimeZoneTool {

    @Tool(
        name = "current_date_time_zone",
        description = "Get current date time with timezone"
    )
    public String getCurrentDateTimeZone() {
        return java.time.LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
    
}

//package com.pdh.booking.mcp;
//
//import org.springframework.ai.tool.ToolCallbackProvider;
//import org.springframework.ai.tool.method.MethodToolCallbackProvider;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * Booking MCP Server Configuration
// * Exposes booking operations as AI-callable tools using Spring AI MCP Server
// * Tools are defined in BookingMcpToolService with @Tool annotations
// */
//@Configuration
//public class BookingMcpServer {
//
//    @Bean
//    ToolCallbackProvider bookingTools(BookingMcpToolService bookingMcpToolService) {
//        return MethodToolCallbackProvider.builder()
//                .toolObjects(bookingMcpToolService)
//                .build();
//    }
//}

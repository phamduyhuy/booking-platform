// package com.pdh.flight.mcp;

// import org.springframework.ai.tool.ToolCallbackProvider;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.ai.tool.method.MethodToolCallbackProvider;
// import com.pdh.flight.controller.FlightController;

// @Configuration
// public class FlightMcpServer {
//     @Bean
//     ToolCallbackProvider flightTools(FlightController flightController){
//         return MethodToolCallbackProvider.builder().toolObjects(flightController).build();
//     }

// }

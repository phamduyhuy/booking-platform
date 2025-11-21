// package com.pdh.hotel.mcp;

// import org.springframework.ai.tool.ToolCallbackProvider;
// import org.springframework.ai.tool.method.MethodToolCallbackProvider;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;

// import com.pdh.hotel.controller.HotelController;

// import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;

// @Configuration
// public class HotelMcpServer {


//     @Bean
//     ToolCallbackProvider hotelTools(HotelController hotelController){
        
//         return MethodToolCallbackProvider.builder().toolObjects(hotelController).build();
//     }

// }

package com.pdh.ai.config;

import com.pdh.ai.agent.BookingSmartAssistant;
import com.pdh.ai.agent.ExploreAgent;
import com.pdh.ai.agent.tools.CurrentDateTimeZoneTool;
import com.pdh.ai.agent.workflow.BookingIntent;
import com.pdh.ai.agent.workflow.ConversationRouterAgent;
import com.pdh.ai.agent.workflow.SmallTalkAgent;
import com.pdh.ai.agent.workflow.TravelFulfillmentAgent;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.service.JpaChatMemoryStore;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
@EnableConfigurationProperties(McpClientProperties.class)
public class BookingSmartAgentConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true", matchIfMissing = false)
    public McpClientManager mcpClientManager(McpClientProperties properties,
            ClientCredentialsTokenManager tokenManager) {
        return new McpClientManager(properties, tokenManager);
    }

    // @Bean
    // public BookingSmartAssistant bookingSmartAssistant(JpaChatMemoryStore chatMemoryStore,
    //         ChatModel chatModel,
    //         ObjectProvider<McpToolProvider> mcpToolProvider,
    //         ObjectProvider<CurrentDateTimeZoneTool> currentDateTimeZoneTool) {
    //     ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
    //             .id(memoryId)
    //             .maxMessages(15)
    //             .chatMemoryStore(chatMemoryStore)
    //             .build();

    //     ConversationRouterAgent conversationRouter = AgenticServices.agentBuilder(ConversationRouterAgent.class)
    //             .chatModel(chatModel)
    //             .outputKey("intent")
    //             .beforeAgentInvocation(req -> {
    //                 // Should include "request" -> "...user text..."
    //                 System.out.println("Router inputs: " + req.inputs());
    //             })
    //             .build();

    //     var fulfillmentBuilder = AgenticServices.agentBuilder(TravelFulfillmentAgent.class)
    //             .chatModel(chatModel)
    //             .chatMemoryProvider(chatMemoryProvider)
    //             .outputKey("structuredResponse");

    //     McpToolProvider provider = mcpToolProvider.getIfAvailable();
    //     if (provider != null) {
    //         fulfillmentBuilder.toolProvider(provider);
    //     }

    //     CurrentDateTimeZoneTool timeTool = currentDateTimeZoneTool.getIfAvailable();
    //     if (timeTool != null) {
    //         fulfillmentBuilder.tools(timeTool);
    //     }

    //     TravelFulfillmentAgent fulfillmentAgent = fulfillmentBuilder.build();

    //     SmallTalkAgent smallTalkAgent = AgenticServices.agentBuilder(SmallTalkAgent.class)
    //             .chatModel(chatModel)
    //             .chatMemoryProvider(chatMemoryProvider)
    //             .outputKey("structuredResponse")
    //             .build();

    //     UntypedAgent routedWorkflow = AgenticServices
    //             .conditionalBuilder()
    //             .subAgents(scope -> scope.readState("intent", BookingIntent.UNKNOWN) == BookingIntent.SMALL_TALK,
    //                     smallTalkAgent)
    //             .subAgents(scope -> true, fulfillmentAgent)
    //             .outputKey("structuredResponse")
    //             .build();

    //     return AgenticServices
    //             .sequenceBuilder(BookingSmartAssistant.class)
    //             .subAgents(conversationRouter, routedWorkflow)
    //             .errorHandler(ctx -> {
    //                 var ex = ctx.exception();
    //                 if (ex instanceof MissingArgumentException m && "request".equals(m.argumentName())) {
    //                     // Try to bridge from a legacy key or fail gracefully
    //                     String legacy = ctx.agenticScope().readState("userMessage", (String) null);
    //                     if (legacy != null && !legacy.isBlank()) {
    //                         ctx.agenticScope().writeState("request", legacy);
    //                         return ErrorRecoveryResult.retry();
    //                     }
    //                     // You can also inject a safe default to keep the flow moving
    //                     ctx.agenticScope().writeState("request", "");
    //                     return ErrorRecoveryResult.retry();
    //                 }
    //                 return ErrorRecoveryResult.throwException();
    //             })
    //             .outputKey("structuredResponse")
    //             .output(scope -> {
    //                 StructuredChatPayload payload = (StructuredChatPayload) scope.readState("structuredResponse");
    //                 return payload != null ? payload
    //                         : StructuredChatPayload.builder()
    //                                 .message("Xin lỗi, tôi không thể xử lý yêu cầu của bạn ngay bây giờ.")
    //                                 .results(Collections.emptyList())
    //                                 .nextRequestSuggestions(new String[0])
    //                                 .requiresConfirmation(false)
    //                                 .build();
    //             })
    //             .build();
    // }

    @Bean
    public ExploreAgent exploreAgent(ChatModel chatModel,
            ObjectProvider<McpToolProvider> mcpToolProvider,
            ObjectProvider<CurrentDateTimeZoneTool> currentDateTimeZoneTool) {
        var builder = AgenticServices.agentBuilder(ExploreAgent.class)
                .chatModel(chatModel);

        McpToolProvider provider = mcpToolProvider.getIfAvailable();
        if (provider != null) {
            builder.toolProvider(provider);
        }

        CurrentDateTimeZoneTool timeTool = currentDateTimeZoneTool.getIfAvailable();
        if (timeTool != null) {
            builder.tools(timeTool);
        }

        return builder.build();
    }
}

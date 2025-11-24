// package com.pdh.ai.rag.service;

// import com.pdh.ai.model.dto.StructuredChatPayload;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
// import org.springframework.ai.converter.BeanOutputConverter;
// import org.springframework.ai.mistralai.MistralAiChatModel;
// import org.springframework.ai.vectorstore.SearchRequest;
// import org.springframework.ai.vectorstore.VectorStore;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// @Service
// public class RagService {

//     private final ChatClient chatClient;
//     private final VectorStore vectorStore;

//     @Autowired
//     public RagService(MistralAiChatModel mistralAiChatModel, VectorStore vectorStore) {
//         this.vectorStore = vectorStore;
        
//         // Create QuestionAnswerAdvisor for RAG following Spring AI documentation
//         QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
//                 .searchRequest(SearchRequest.builder()
//                         .similarityThreshold(0.7d)
//                         .topK(5)
//                         .build())
//                 .build();
        
//         this.chatClient = ChatClient.builder(mistralAiChatModel)
//                 .defaultAdvisors(qaAdvisor)
//                 .build();
//     }

//     /**
//      * Process a user query using RAG (Retrieval Augmented Generation).
//      *
//      * @param query The user's query
//      * @param conversationId The conversation ID for context
//      * @return StructuredChatPayload with the response
//      */
//     public StructuredChatPayload processQueryWithRag(String query, String conversationId) {
//         BeanOutputConverter<StructuredChatPayload> converter = new BeanOutputConverter<>(StructuredChatPayload.class);
        
//         String customPromptTemplate = """
//                 You are BookingSmart AI Travel Assistant with access to company knowledge base.
//                 Use the provided context information to answer the user's question accurately.
                
//                 Context information is below.
//                 ---------------------
//                 {question_answer_context}
//                 ---------------------
                
//                 Given the context information and no prior knowledge, answer the query.
//                 Query: {query}
                
//                 Please provide your response in the structured format specified.
//                 {format}
//                 """;

//         StructuredChatPayload result = chatClient.prompt()
//                 .user(u -> u.text(customPromptTemplate)
//                         .param("query", query)
//                         .param("format", converter.getJsonSchema()))
//                 .advisors(advisorSpec -> advisorSpec
//                         .param("conversationId", conversationId))
//                 .call()
//                 .entity(StructuredChatPayload.class);

//         return result;
//     }
    
//     /**
//      * Process a user query using RAG with a simpler approach.
//      *
//      * @param query The user's query
//      * @param conversationId The conversation ID for context
//      * @return StructuredChatPayload with the response
//      */
//     public StructuredChatPayload processQueryWithRagSimple(String query, String conversationId) {
//         BeanOutputConverter<StructuredChatPayload> converter = new BeanOutputConverter<>(StructuredChatPayload.class);
        
//         StructuredChatPayload result = chatClient.prompt()
//                 .user(u -> u.text(query + "\n{format}")
//                         .param("format", converter.getFormat()))
//                 .advisors(advisorSpec -> advisorSpec
//                         .param("conversationId", conversationId))
//                 .call()
//                 .entity(StructuredChatPayload.class);

//         return result;
//     }
// }
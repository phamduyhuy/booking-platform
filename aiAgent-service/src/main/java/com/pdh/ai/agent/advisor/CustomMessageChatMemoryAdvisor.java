

package com.pdh.ai.agent.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

import com.pdh.ai.service.JpaChatMemory;

import lombok.extern.slf4j.Slf4j;

/**
 * Memory is retrieved added as a collection of messages to the prompt
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Slf4j
public final class CustomMessageChatMemoryAdvisor implements BaseChatMemoryAdvisor {
	private final JpaChatMemory chatMemory;
    public  static final String ADD_USER_MESSAGE = "addUserMessage";
	private final String defaultConversationId;
	private final int order;
    private final boolean isAddUserMessage;

	private final Scheduler scheduler;

	private CustomMessageChatMemoryAdvisor(JpaChatMemory chatMemory, String defaultConversationId, int order,
			Scheduler scheduler, boolean isAddUserMessage) {
		Assert.notNull(chatMemory, "chatMemory cannot be null");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
		Assert.notNull(scheduler, "scheduler cannot be null");
		this.chatMemory = chatMemory;
		this.defaultConversationId = defaultConversationId;
		this.order = order;
		this.scheduler = scheduler;
        this.isAddUserMessage = isAddUserMessage;
	}

    public boolean isAddUserMessage(Map<String, Object> context, boolean isAddUserMessage) {
        Assert.notNull(context, "context cannot be null");
        Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
        return  context.containsKey(ADD_USER_MESSAGE) && isAddUserMessage;

    }
	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		String conversationId = getConversationId(chatClientRequest.context(), this.defaultConversationId);
        boolean isAddUserMessage = isAddUserMessage(chatClientRequest.context(), this.isAddUserMessage);

		// 1. Retrieve the chat memory for the current conversation.
		// Use getForAgent() to filter USER, ASSISTANT, and TOOL messages for agent context
		List<Message> memoryMessages = this.chatMemory.getForAgent(conversationId);
		log.debug("Retrieved {} messages from chat memory for agent context (conversationId={})", memoryMessages.size(),
				conversationId);
		// 2. Advise the request messages list.
		List<Message> processedMessages = new ArrayList<>(memoryMessages);
		processedMessages.addAll(chatClientRequest.prompt().getInstructions());

		// 3. Create a new request with the advised messages.
		ChatClientRequest processedChatClientRequest = chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().mutate().messages(processedMessages).build())
			.build();

		

		return processedChatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		List<Message> assistantMessages = new ArrayList<>();
		if (chatClientResponse.chatResponse() != null) {
			assistantMessages = chatClientResponse.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();
		}
		if(assistantMessages!=null && !assistantMessages.isEmpty() ) {
			String conversationId = getConversationId(chatClientResponse.context(), this.defaultConversationId);
			// this.chatMemory.add(conversationId, assistantMessages);
			log.debug("assistant messages to chat memory for conversationId={}, message: {}", conversationId,
					chatClientResponse.context());
		}
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		// Get the scheduler from BaseAdvisor
		Scheduler scheduler = this.getScheduler();

		// Process the request with the before method
		return Mono.just(chatClientRequest)
			.publishOn(scheduler)
			.map(request -> this.before(request, streamAdvisorChain))
			.flatMapMany(streamAdvisorChain::nextStream)
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					response -> this.after(response, streamAdvisorChain)));
	}

	public static Builder builder(JpaChatMemory chatMemory) {
		return new Builder(chatMemory);
	}

	public static final class Builder {

		private String conversationId = JpaChatMemory.DEFAULT_CONVERSATION_ID;

		private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private JpaChatMemory chatMemory;
        private boolean isAddUserMessage = true;

		private Builder(JpaChatMemory chatMemory) {
			this.chatMemory = chatMemory;
		}

		/**
		 * Set the conversation id.
		 * @param conversationId the conversation id
		 * @return the builder
		 */
		public Builder conversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		/**
		 * Set the order.
		 * @param order the order
		 * @return the builder
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}
        public Builder isAddUserMessage(boolean isAddUserMessage) {
            this.isAddUserMessage = isAddUserMessage;
            return this;
        }

		/**
		 * Build the advisor.
		 * @return the advisor
		 */
		public CustomMessageChatMemoryAdvisor build() {
			return new CustomMessageChatMemoryAdvisor(this.chatMemory, this.conversationId, this.order, this.scheduler,this.isAddUserMessage);
		}

	}

}

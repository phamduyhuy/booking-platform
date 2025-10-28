import { apiClient } from '@/lib/api-client';
import {
  ChatResponse,
  ChatContext,
  ChatHistoryResponse,
  ChatMessageRequest,
  StructuredChatPayload,
  ChatConversationSummary,
  ChatMessageResponse,
} from '../types';

type PendingChatRequest = {
  requestId: string;
  conversationId: string;
  resolve: (response: ChatMessageResponse) => void;
  reject: (error: Error) => void;
  onEvent?: (event: ChatMessageResponse) => void;
};

class AiChatService {
  private baseUrl = '/ai';
  private socket: WebSocket | null = null;
  private socketReady: Promise<WebSocket> | null = null;
  private currentRequest: PendingChatRequest | null = null;

  private generateRequestId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `ws-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }

  private generateConversationId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }

  private getWebSocketUrl(): string {
    if (typeof window === 'undefined') {
      throw new Error('WebSocket chat requires a browser environment');
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    return `${protocol}//${host}/api/ai/ws/chat`;
  }

  private normalizeTimestamp(value?: string): string {
    if (!value) {
      return new Date().toISOString();
    }
    return value;
  }

  private cleanupSocket() {
    this.currentRequest = null;
    if (this.socket) {
      this.socket.removeEventListener('message', this.handleSocketMessage);
      this.socket.removeEventListener('close', this.handleSocketClose);
      this.socket.removeEventListener('error', this.handleSocketError);
    }
    this.socket = null;
    this.socketReady = null;
  }

  private handleSocketError = (event: Event) => {
    console.warn('AI chat WebSocket error:', event);
    const pending = this.currentRequest;
    this.currentRequest = null;
    if (pending) {
      pending.reject(new Error('WebSocket connection error'));
    }
    this.cleanupSocket();
  };

  private handleSocketClose = (event: CloseEvent) => {
    const pending = this.currentRequest;
    this.currentRequest = null;
    this.cleanupSocket();

    if (pending) {
      const reason =
        event.reason && event.reason.length > 0
          ? event.reason
          : `WebSocket closed (code ${event.code})`;
      pending.reject(new Error(reason));
    }
  };

  private handleSocketMessage = (event: MessageEvent) => {
    let data: ChatMessageResponse;
    try {
      data = JSON.parse(event.data) as ChatMessageResponse;
    } catch (err) {
      console.error('Failed to parse AI chat payload', err);
      return;
    }

    if (data.status === 'keepalive') {
      return;
    }

    const pending = this.currentRequest;
    if (!pending) {
      console.warn('Received AI chat event with no pending request', data);
      return;
    }

    data.requestId = data.requestId ?? pending.requestId;
    data.conversationId = data.conversationId ?? pending.conversationId;
    data.timestamp = this.normalizeTimestamp(data.timestamp);

    pending.onEvent?.(data);

    if (data.type === 'ERROR') {
      this.currentRequest = null;
      pending.reject(new Error(data.error || 'AI assistant encountered an error'));
      return;
    }

    if (data.type === 'RESPONSE') {
      this.currentRequest = null;
      pending.resolve(data);
    }
  };

  private async ensureSocket(): Promise<WebSocket> {
    if (typeof window === 'undefined') {
      throw new Error('WebSocket chat requires a browser environment');
    }

    if (this.socket) {
      if (this.socket.readyState === WebSocket.OPEN) {
        return this.socket;
      }
      if (this.socket.readyState === WebSocket.CONNECTING && this.socketReady) {
        return this.socketReady;
      }
    }

    const wsUrl = this.getWebSocketUrl();
    const socket = new WebSocket(wsUrl);
    this.socket = socket;

    this.socketReady = new Promise<WebSocket>((resolve, reject) => {
      const handleOpen = () => {
        socket.removeEventListener('open', handleOpen);
        socket.removeEventListener('error', handleInitialError);
        socket.addEventListener('message', this.handleSocketMessage);
        socket.addEventListener('close', this.handleSocketClose);
        socket.addEventListener('error', this.handleSocketError);
        resolve(socket);
      };

      const handleInitialError = (event: Event) => {
        socket.removeEventListener('open', handleOpen);
        socket.removeEventListener('error', handleInitialError);
        this.cleanupSocket();
        reject(new Error('WebSocket connection error'));
      };

      socket.addEventListener('open', handleOpen, { once: true });
      socket.addEventListener('error', handleInitialError, { once: true });
    });

    return this.socketReady;
  }

  private mapPayloadToChatResponse(
    payload: StructuredChatPayload,
    overrides?: { userMessage?: string; conversationId?: string }
  ): ChatResponse {
    const suggestionsRaw = payload.nextRequestSuggestions ?? payload.next_request_suggestions;
    const suggestions = Array.isArray(suggestionsRaw) ? suggestionsRaw : [];
    const conversationId = overrides?.conversationId || this.generateConversationId();
    return {
      userMessage: overrides?.userMessage ?? '',
      aiResponse: payload.message ?? '',
      conversationId,
      timestamp: new Date().toISOString(),
      results: payload.results ?? [],
      nextRequestSuggestions: suggestions,
      requiresConfirmation: payload.requiresConfirmation,
      confirmationContext: payload.confirmationContext
    };
  }

  async streamPrompt(
    message: string,
    options: {
      conversationId?: string;
      onEvent?: (event: ChatMessageResponse) => void;
    } = {}
  ): Promise<ChatMessageResponse> {
    const trimmed = message.trim();
    if (!trimmed) {
      throw new Error('Message cannot be empty');
    }

    if (this.currentRequest) {
      throw new Error('Another AI chat request is already in progress. Please wait for it to complete.');
    }

    const requestId = this.generateRequestId();
    const conversationId = options.conversationId ?? this.generateConversationId();
    const socket = await this.ensureSocket();

    return new Promise<ChatMessageResponse>((resolve, reject) => {
      const pendingRequest: PendingChatRequest = {
        requestId,
        conversationId,
        resolve,
        reject,
        onEvent: options.onEvent,
      };

      this.currentRequest = pendingRequest;

      const payload = {
        type: 'prompt',
        requestId,
        conversationId,
        message: trimmed,
        timestamp: Date.now(),
      };

      try {
        socket.send(JSON.stringify(payload));
      } catch (err: any) {
        this.currentRequest = null;
        this.cleanupSocket();
        reject(err instanceof Error ? err : new Error(String(err)));
      }
    });
  }

  /**
   * Send a message to the AI chatbot (Synchronous - waits for complete response)
   * Uses REST API with Agentic Workflow Orchestration (Routing + Parallelization + Evaluation)
   * Note: userId is automatically extracted from JWT token on backend
   */
  async sendMessage(
    message: string, 
    context?: ChatContext
  ): Promise<ChatResponse> {
    const trimmed = message.trim();
    if (!trimmed) {
      throw new Error('Message cannot be empty');
    }

    const conversationId = context?.conversationId ?? this.generateConversationId();

    try {
      const response = await this.streamPrompt(trimmed, {
        conversationId,
      });

      const suggestions = response.nextRequestSuggestions ?? response.next_request_suggestions ?? [];
      return {
        userMessage: message,
        aiResponse: response.aiResponse ?? '',
        conversationId: response.conversationId ?? conversationId,
        requestId: response.requestId,
        userId: response.userId,
        timestamp: this.normalizeTimestamp(response.timestamp),
        results: response.results ?? [],
        nextRequestSuggestions: suggestions,
        requiresConfirmation: response.requiresConfirmation,
        confirmationContext: response.confirmationContext,
      };
    } catch (error: any) {
      console.error('AI Chat Service Error:', error);

      // Attempt REST fallback if WebSocket fails
      try {
        const restPayload: ChatMessageRequest = {
          message: trimmed,
          conversationId,
          timestamp: Date.now(),
        };
        const restResponse = await apiClient.post<StructuredChatPayload>(
          `${this.baseUrl}/chat/message`,
          restPayload
        );

        return this.mapPayloadToChatResponse(restResponse, {
          userMessage: message,
          conversationId,
        });
      } catch (restError: any) {
        console.error('AI Chat REST fallback error:', restError);
      }
      
      // Return a fallback response
      return {
        userMessage: message,
        aiResponse: 'Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.',
        conversationId,
        timestamp: new Date().toISOString(),
        error: error?.message || 'Không thể kết nối với AI service',
        results: [],
        nextRequestSuggestions: []
      };
    }
  }

  /**
   * Get chat history for a conversation
   */
  async getChatHistory(conversationId: string): Promise<ChatHistoryResponse> {
    try {
      const response = await apiClient.get<ChatHistoryResponse>(
        `${this.baseUrl}/chat/history/${conversationId}`
      );
      return response;
    } catch (error: any) {
      console.error('Error getting chat history:', error);
      return {
        conversationId,
        messages: [],
        createdAt: new Date().toISOString(),
        lastUpdated: new Date().toISOString()
      };
    }
  }

  /**
   * List user's conversations
   */
  async listConversations(): Promise<ChatConversationSummary[]> {
    try {
      const response = await apiClient.get<ChatConversationSummary[]>(
        `${this.baseUrl}/chat/conversations`
      );
      return response;
    } catch (error) {
      console.error('Error listing conversations:', error);
      return [];
    }
  }

  /**
   * Clear chat history for a conversation
   */
  async clearChatHistory(conversationId: string): Promise<boolean> {
    try {
      await apiClient.delete(`${this.baseUrl}/chat/history/${conversationId}`);
      return true;
    } catch (error) {
      console.error('Error clearing chat history:', error);
      return false;
    }
  }

  /**
   * Get chat suggestions based on current context
   */
  async getChatSuggestions(context?: ChatContext): Promise<string[]> {
    try {
      return [
        "Tìm chuyến bay từ Hồ Chí Minh đến Đà Nẵng",
        "Gợi ý khách sạn 4 sao tại Đà Nẵng", 
        "Lập kế hoạch du lịch 3 ngày 2 đêm",
        "Tìm địa điểm ăn uống ngon tại Hội An"
      ];
    } catch (error) {
      console.error('Error getting chat suggestions:', error);
      return [];
    }
  }

  async getDefaultDestinations(): Promise<ChatResponse> {
    try {
      const payload = await apiClient.get<StructuredChatPayload>(
        `${this.baseUrl}/explore/default`
      );
      return this.mapPayloadToChatResponse(payload, {
        conversationId: `explore-${this.generateConversationId()}`
      });
    } catch (error: any) {
      console.error('Get Default Destinations Error:', error);
      return this.mapPayloadToChatResponse({
        message: 'Xin lỗi, tôi không thể tải gợi ý du lịch lúc này.',
        results: [],
        next_request_suggestions: []
      }, { conversationId: `explore-${this.generateConversationId()}` });
    }
  }

  async exploreDestinations(query: string, userCountry?: string): Promise<ChatResponse> {
    try {
      const params: any = { query };
      if (userCountry) {
        params.userCountry = userCountry;
      }
      const payload = await apiClient.get<StructuredChatPayload>(
        `${this.baseUrl}/explore`,
        { params }
      );
      return this.mapPayloadToChatResponse(payload, {
        userMessage: query,
        conversationId: `explore-${this.generateConversationId()}`
      });
    } catch (error: any) {
      console.error('Explore Destinations Error:', error);
      return this.mapPayloadToChatResponse({
        message: 'Xin lỗi, tôi không thể tìm kiếm địa điểm lúc này. Vui lòng thử lại sau.',
        results: [],
        next_request_suggestions: []
      }, {
        userMessage: query,
        conversationId: `explore-${this.generateConversationId()}`
      });
    }
  }

  async getTrendingDestinations(userCountry?: string): Promise<ChatResponse> {
    try {
      const params: any = {};
      if (userCountry) {
        params.userCountry = userCountry;
      }
      const payload = await apiClient.get<StructuredChatPayload>(
        `${this.baseUrl}/explore/trending`,
        { params }
      );
      return this.mapPayloadToChatResponse(payload, {
        userMessage: 'Gợi ý điểm đến thịnh hành',
        conversationId: `explore-${this.generateConversationId()}`
      });
    } catch (error: any) {
      console.error('Get Trending Destinations Error:', error);
      return this.mapPayloadToChatResponse({
        message: 'Xin lỗi, tôi không thể tải điểm đến phổ biến lúc này.',
        results: [],
        next_request_suggestions: []
      }, {
        userMessage: 'Gợi ý điểm đến thịnh hành',
        conversationId: `explore-${this.generateConversationId()}`
      });
    }
  }

  async getSeasonalDestinations(season?: string, userCountry?: string): Promise<ChatResponse> {
    try {
      const params: any = {};
      if (season) {
        params.season = season;
      }
      if (userCountry) {
        params.userCountry = userCountry;
      }
      const payload = await apiClient.get<StructuredChatPayload>(
        `${this.baseUrl}/explore/seasonal`,
        { params }
      );
      const userMessage = season
        ? `Gợi ý điểm đến phù hợp mùa ${season}`
        : 'Gợi ý điểm đến theo mùa';
      return this.mapPayloadToChatResponse(payload, {
        userMessage,
        conversationId: `explore-${this.generateConversationId()}`
      });
    } catch (error: any) {
      console.error('Get Seasonal Destinations Error:', error);
      const userMessage = season
        ? `Gợi ý điểm đến phù hợp mùa ${season}`
        : 'Gợi ý điểm đến theo mùa';
      return this.mapPayloadToChatResponse({
        message: 'Xin lỗi, tôi không thể tải gợi ý theo mùa lúc này.',
        results: [],
        next_request_suggestions: []
      }, {
        userMessage,
        conversationId: `explore-${this.generateConversationId()}`
      });
    }
  }
}

export const aiChatService = new AiChatService();
export default aiChatService;

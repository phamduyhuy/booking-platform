import { useState, useCallback, useEffect, useRef } from 'react';
import { aiChatService } from '../service';
import { ChatMessage, ChatContext, ChatStructuredResult, ChatMessageResponse } from '../types';
import { useAuth } from '@/contexts/auth-context';

interface UseAiChatOptions {
    conversationId?: string;
    loadHistoryOnMount?: boolean;
    context?: ChatContext;
    onError?: (error: string) => void;
}

interface UseAiChatReturn {
    messages: ChatMessage[];
    isLoading: boolean;
    error: string | null;
    conversationId: string | null;
    sendMessage: (message: string) => Promise<void>;
    clearMessages: () => void;
    startNewConversation: () => void;
    loadChatHistory: () => Promise<void>;
    suggestions: string[];
    getSuggestions: () => Promise<void>;
    handleConfirmation: (confirmed: boolean, confirmationContext?: any) => Promise<void>;
}

interface ParsedStructuredPayload {
    message: string;
    results: ChatStructuredResult[];
    suggestions: string[];
}

const parseStructuredPayload = (raw?: string | null): ParsedStructuredPayload | null => {
    if (!raw) {
        return null;
    }

    let content = raw.trim();
    if (!content) {
        return null;
    }

    console.log('🔍 Parsing structured payload, raw length:', content.length);
    console.log('🔍 First 200 chars:', content.substring(0, 200));

    if (content.startsWith('```')) {
        const newlineIndex = content.indexOf('\n');
        if (newlineIndex > 0) {
            content = content.substring(newlineIndex + 1);
        } else {
            content = content.substring(3);
        }
    }

    if (content.endsWith('```')) {
        content = content.substring(0, content.length - 3);
    }

    content = content.trim();

    if (content.startsWith('json')) {
        content = content.substring(4).trim();
    }

    const tryParse = (value: string): any => {
        try {
            const result = JSON.parse(value);
            console.log('✅ Parse successful:', Object.keys(result));
            return result;
        } catch (err) {
            console.log('❌ Parse failed:', err);
            return null;
        }
    };

    let parsed: any = tryParse(content);

    if (!parsed) {
        const start = content.indexOf('{');
        const end = content.lastIndexOf('}');
        if (start !== -1 && end !== -1 && end > start) {
            parsed = tryParse(content.substring(start, end + 1));
        }
    }

    if (!parsed || typeof parsed !== 'object') {
        return null;
    }

    const message = typeof parsed.message === 'string' && parsed.message.trim().length > 0
        ? parsed.message
        : raw;

    const results = Array.isArray(parsed.results)
        ? parsed.results.filter(Boolean) as ChatStructuredResult[]
        : [];

    const rawSuggestions = parsed.next_request_suggestions ?? parsed.nextRequestSuggestions ?? [];
    const suggestionArray = Array.isArray(rawSuggestions)
        ? rawSuggestions
        : rawSuggestions && typeof rawSuggestions === 'object'
            ? Object.values(rawSuggestions as Record<string, unknown>)
            : [];

    const suggestions: string[] = suggestionArray
        .map((item: unknown) => {
            if (typeof item === 'string') return item.trim();
            if (item && typeof item === 'object' && 'value' in item && typeof (item as { value: unknown }).value === 'string') {
                return ((item as { value: string }).value).trim();
            }
            return String(item ?? '').trim();
        })
        .filter((item: string) => item.length > 0);

    return { message, results, suggestions };
};

const createConversationId = (): string => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const buildConversationTitle = (message: string): string => {
    if (!message) {
        return 'Cuộc trò chuyện';
    }
    const sanitized = message.replace(/\s+/g, ' ').trim();
    if (!sanitized) {
        return 'Cuộc trò chuyện';
    }
    const maxLength = 60;
    return sanitized.length <= maxLength ? sanitized : `${sanitized.slice(0, maxLength)}...`;
};

const createInitialMessages = (): ChatMessage[] => [
    {
        id: '1',
        content: 'Xin chào! Tôi có thể giúp bạn tìm kiếm chuyến bay, khách sạn hoặc lên kế hoạch du lịch. Bạn muốn đi đâu?',
        isUser: false,
        timestamp: new Date(),
        results: [],
        suggestions: [],
    },
];

const createMessageId = (prefix: 'user' | 'assistant'): string => {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
};


export function useAiChat(options: UseAiChatOptions = {}): UseAiChatReturn {
    const {
        conversationId: initialConversationId,
        loadHistoryOnMount = false,
        context,
        onError
    } = options;

    const [messages, setMessages] = useState<ChatMessage[]>(() => createInitialMessages());
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [conversationId, setConversationId] = useState<string | null>(initialConversationId || null);
    const [suggestions, setSuggestions] = useState<string[]>([]);
    const { refreshChatConversations, upsertChatConversation } = useAuth();
    const conversationIdRef = useRef<string | null>(conversationId);
    const onErrorRef = useRef(onError);

    useEffect(() => {
        conversationIdRef.current = conversationId;
    }, [conversationId]);

    useEffect(() => {
        onErrorRef.current = onError;
    }, [onError]);

    useEffect(() => {
        if (initialConversationId === undefined) {
            return;
        }

        if (initialConversationId !== conversationId) {
            setConversationId(initialConversationId || null);
        }
    }, [initialConversationId, conversationId]);

    useEffect(() => {
        if (loadHistoryOnMount && conversationId) {
            loadChatHistory();
        }
    }, [conversationId, loadHistoryOnMount]);

    const loadChatHistory = useCallback(async () => {
        if (!conversationId) return;

        try {
            const historyResponse = await aiChatService.getChatHistory(conversationId);

            if (historyResponse.messages && historyResponse.messages.length > 0) {
                const historyMessages: ChatMessage[] = historyResponse.messages.map((msg, index) => {
                    const parsed = parseStructuredPayload(msg.content);

                    return {
                        id: `history-${index}`,
                        content: parsed?.message ?? msg.content,
                        isUser: msg.role === 'user',
                        timestamp: new Date(msg.timestamp),
                        results: [...(parsed?.results ?? [])],
                        suggestions: [...(parsed?.suggestions ?? [])],
                    };
                });

                setMessages(historyMessages);
            }
        } catch (err) {
            console.error('Failed to load chat history:', err);
        }
    }, [conversationId]);

    const sendMessage = useCallback(async (messageContent: string) => {
        const trimmedMessage = messageContent.trim();
        if (!trimmedMessage || isLoading) {
            return;
        }

        const baseConversationId = conversationId || context?.conversationId;
        const isExistingConversation = Boolean(baseConversationId);
        const effectiveConversationId = baseConversationId ?? createConversationId();
        const conversationTitle = buildConversationTitle(trimmedMessage);
        const userMessageId = createMessageId('user');
        const assistantMessageId = createMessageId('assistant');
        const now = new Date();

        const userMessage: ChatMessage = {
            id: userMessageId,
            content: trimmedMessage,
            isUser: true,
            timestamp: now,
            results: [],
            suggestions: [],
        };

        const assistantPlaceholder: ChatMessage = {
            id: assistantMessageId,
            content: '',
            isUser: false,
            timestamp: now,
            results: [],
            suggestions: [],
        };

        setMessages(prev => [...prev, userMessage, assistantPlaceholder]);
        setIsLoading(true);
        setError(null);

        if (!conversationId) {
            setConversationId(effectiveConversationId);

            upsertChatConversation({
                id: effectiveConversationId,
                title: conversationTitle,
                createdAt: now.toISOString(),
                lastUpdated: now.toISOString(),
            });
        }

        const updateAssistantMessage = (event: ChatMessageResponse) => {
            setMessages(prev => {
                const next = [...prev];
                const idx = next.findIndex(msg => msg.id === assistantMessageId);
                if (idx === -1) {
                    return next;
                }

                const existing = next[idx];
                const safeTimestamp = event.timestamp ? new Date(event.timestamp) : existing.timestamp;
                const suggestionsField = event.nextRequestSuggestions ?? event.next_request_suggestions;
                const nextSuggestions = suggestionsField !== undefined
                    ? [...suggestionsField]
                    : existing.suggestions ?? [];
                const hasResults = Array.isArray(event.results) && event.results.length > 0;

                const updatedContent =
                    event.aiResponse && event.aiResponse.trim().length > 0
                        ? event.aiResponse
                        : existing.content;

                const updatedResults = hasResults
                    ? [...(event.results as ChatStructuredResult[])]
                    : existing.results ?? [];

                next[idx] = {
                    ...existing,
                    content: updatedContent,
                    results: updatedResults,
                    suggestions: nextSuggestions,
                    requiresConfirmation: event.requiresConfirmation ?? existing.requiresConfirmation,
                    confirmationContext: event.confirmationContext ?? existing.confirmationContext,
                    timestamp: safeTimestamp,
                };

                return next;
            });
        };

        try {
            const finalResponse = await aiChatService.streamPrompt(trimmedMessage, {
                conversationId: effectiveConversationId,
                onEvent: (event) => {
                    if (event.status === 'keepalive') {
                        return;
                    }

                    if (!conversationIdRef.current && event.conversationId) {
                        setConversationId(event.conversationId);
                    }

                    updateAssistantMessage(event);

                    if (event.conversationId) {
                        const timestampIso = event.timestamp ?? new Date().toISOString();
                        upsertChatConversation({
                            id: event.conversationId,
                            title: isExistingConversation ? '' : conversationTitle,
                            lastUpdated: timestampIso,
                            createdAt: isExistingConversation ? undefined : timestampIso,
                        });
                    }
                },
            });

            console.log('✅ WebSocket stream completed successfully');

            const responseConversationId = finalResponse.conversationId ?? effectiveConversationId;

            if (conversationId !== responseConversationId) {
                setConversationId(responseConversationId);
            }

            const lastUpdatedIso = finalResponse.timestamp ?? new Date().toISOString();
            const suggestionsField = finalResponse.nextRequestSuggestions ?? finalResponse.next_request_suggestions ?? [];

            upsertChatConversation({
                id: responseConversationId,
                title: isExistingConversation ? '' : conversationTitle,
                lastUpdated: lastUpdatedIso,
                createdAt: isExistingConversation ? undefined : lastUpdatedIso,
            });

            setMessages(prev => {
                const next = [...prev];
                const idx = next.findIndex(msg => msg.id === assistantMessageId);
                if (idx === -1) {
                    return next;
                }

                next[idx] = {
                    ...next[idx],
                    content: finalResponse.aiResponse ?? '',
                    results: finalResponse.results ?? [],
                    suggestions: suggestionsField,
                    requiresConfirmation: finalResponse.requiresConfirmation ?? false,
                    confirmationContext: finalResponse.confirmationContext,
                    timestamp: new Date(lastUpdatedIso),
                };
                return next;
            });

            refreshChatConversations().catch((err) => {
                console.error('Failed to refresh conversations:', refreshErr);
            });
        } catch (err: any) {
            console.log('⚠️ WebSocket failed, attempting REST fallback...');
            
            try {
                const fallbackResponse = await aiChatService.sendPromptRest(trimmedMessage, {
                    conversationId: effectiveConversationId,
                });

                console.log('✅ REST fallback succeeded');

                const responseConversationId = fallbackResponse.conversationId ?? effectiveConversationId;
                const fallbackTimestampIso = fallbackResponse.timestamp ?? new Date().toISOString();
                const fallbackSuggestions = fallbackResponse.nextRequestSuggestions ?? fallbackResponse.next_request_suggestions ?? [];

                if (conversationIdRef.current !== responseConversationId) {
                    setConversationId(responseConversationId);
                }

                setError(null);

                setMessages(prev => {
                    const next = [...prev];
                    const idx = next.findIndex(msg => msg.id === assistantMessageId);
                    if (idx === -1) {
                        return next;
                    }

                    next[idx] = {
                        ...next[idx],
                        content: fallbackResponse.aiResponse ?? '',
                        results: fallbackResponse.results ?? [],
                        suggestions: fallbackSuggestions,
                        requiresConfirmation: fallbackResponse.requiresConfirmation ?? false,
                        confirmationContext: fallbackResponse.confirmationContext,
                        timestamp: new Date(fallbackTimestampIso),
                    };

                    return next;
                });

                upsertChatConversation({
                    id: responseConversationId,
                    title: isExistingConversation ? '' : conversationTitle,
                    lastUpdated: fallbackTimestampIso,
                    createdAt: isExistingConversation ? undefined : fallbackTimestampIso,
                });

                refreshChatConversations().catch((refreshErr) => {
                    console.error('Failed to refresh conversations:', refreshErr);
                });

                return;
            } catch (fallbackErr: any) {
                console.error('❌ REST fallback also failed:', fallbackErr);
                
                const fallbackTimestamp = new Date();
                const errorMessage = fallbackErr?.message || err?.message || 'Không thể gửi tin nhắn. Vui lòng thử lại.';
                setError(errorMessage);
                onErrorRef.current?.(errorMessage);

                setMessages(prev => {
                    const newMessages = [...prev];
                    const messageIndex = newMessages.findIndex(msg => msg.id === assistantMessageId);

                    if (messageIndex !== -1) {
                        newMessages[messageIndex] = {
                            ...newMessages[messageIndex],
                            content: errorMessage,
                            results: [],
                            timestamp: fallbackTimestamp,
                            suggestions: [],
                            requiresConfirmation: false,
                            confirmationContext: undefined,
                        };
                    }

                    return newMessages;
                });

                upsertChatConversation({
                    id: effectiveConversationId,
                    title: isExistingConversation ? '' : conversationTitle,
                    lastUpdated: fallbackTimestamp.toISOString(),
                    createdAt: isExistingConversation ? undefined : fallbackTimestamp.toISOString(),
                });
            }
        } finally {
            setIsLoading(false);
        }
    }, [isLoading, conversationId, context, refreshChatConversations, upsertChatConversation, onError]);

    const startNewConversation = useCallback(() => {
        setMessages(createInitialMessages());
        setError(null);
        setConversationId(null);
    }, []);

    const clearMessages = useCallback(async () => {
        if (conversationId) {
            try {
                await aiChatService.clearChatHistory(conversationId);
                refreshChatConversations().catch((err) => {
                    console.error('Failed to refresh conversations:', err);
                });
            } catch (err) {
                console.error('Failed to clear chat history:', err);
            }
        }

        setMessages(createInitialMessages());
        setError(null);
        setConversationId(null);
    }, [conversationId, refreshChatConversations]);

    const getSuggestions = useCallback(async () => {
        try {
            const newSuggestions = await aiChatService.getChatSuggestions({
                conversationId: conversationId || undefined,
                ...context
            });
            setSuggestions(newSuggestions);
        } catch (err) {
            console.error('Failed to get suggestions:', err);
        }
    }, [conversationId, context]);

    const handleConfirmation = useCallback(async (confirmed: boolean, confirmationContext?: any) => {
        const message = confirmed ? 'Yes' : 'Cancel';
        console.log('Sending confirmation:', message, confirmationContext);
        await sendMessage(message);
    }, [sendMessage]);

    return {
        messages,
        isLoading,
        error,
        conversationId,
        sendMessage,
        clearMessages,
        startNewConversation,
        loadChatHistory,
        suggestions,
        getSuggestions,
        handleConfirmation,
    };
}

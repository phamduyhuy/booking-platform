"use client"

import React, { useState, useRef, useEffect, forwardRef, useImperativeHandle, useCallback } from "react"
import { Send, Plus, AlertCircle, Mic } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { useAiChat } from "@/modules/ai"
import { useAuth } from "@/contexts/auth-context"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { useDateFormatter } from "@/hooks/use-date-formatter"
import { AiResponseRenderer } from "@/components/ai-response-renderer"
import { useRouter, useSearchParams } from "next/navigation"

interface ChatMessage {
  id: string
  content: string
  isUser: boolean
  timestamp: Date
  results?: any[]
  resultsType?: string
}

interface ChatInterfaceProps {
  onSearchResults: (results: any[], type: string) => void
  onStartBooking: (type: "flight" | "hotel" | "both") => void
  onChatStart: () => void
  onItemSelect?: (item: any) => void
  conversationId?: string | null
  onConversationChange?: (conversationId: string | null) => void
  newChatTrigger?: number
  onFlightBook?: (flight: any) => void
  onHotelBook?: (hotel: any, room: any) => void
  onLocationClick?: (location: { lat: number; lng: number; title: string; description?: string }) => void
}

export const ChatInterface = forwardRef<any, ChatInterfaceProps>(function ChatInterface(
  { onSearchResults, onStartBooking, onChatStart, onItemSelect, conversationId, onConversationChange, newChatTrigger, onFlightBook, onHotelBook, onLocationClick },
  ref,
) {
  const [input, setInput] = useState("")
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const lastConversationRef = useRef<string | null>(conversationId ?? null)
  const newChatRef = useRef<number>(newChatTrigger ?? 0)
  const router = useRouter()
  const searchParams = useSearchParams()
  
  const { formatRelative, formatDateTime } = useDateFormatter()
  const { user } = useAuth()

  // Use the AI chat hook for text messages
  const { 
    messages, 
    isLoading, 
    error, 
    sendMessage, 
    startNewConversation,
    suggestions,
    getSuggestions,
    conversationId: activeConversationId,
    handleConfirmation,
  } = useAiChat({
    conversationId: conversationId ?? undefined,
    loadHistoryOnMount: true,
    // Note: userId is automatically extracted from JWT token on backend
    context: {},
    onError: (errorMsg) => {
      console.error('Chat error:', errorMsg);
    }
  })

  // Confirmation handlers
  const handleConfirm = useCallback((confirmationContext: any) => {
    console.log('User confirmed:', confirmationContext);
    handleConfirmation(true, confirmationContext);
  }, [handleConfirmation]);

  const handleCancel = useCallback(() => {
    console.log('User cancelled confirmation');
    handleConfirmation(false);
  }, [handleConfirmation]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  // Get current suggestions from last message or default
  const currentSuggestions = React.useMemo(() => {
    // Priority 1: Last assistant message suggestions
    const lastAssistantMessage = [...messages].reverse().find(m => !m.isUser);
    if (lastAssistantMessage?.suggestions && lastAssistantMessage.suggestions.length > 0) {
      return lastAssistantMessage.suggestions;
    }
    
    // Priority 2: Default suggestions
    return suggestions;
  }, [messages, suggestions]);

  useEffect(() => {
    // Load suggestions on component mount
    getSuggestions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useImperativeHandle(ref, () => ({
    handleExamplePrompt: (prompt: string) => {
      setInput(prompt)
      handleSubmit(undefined, prompt)
    },
  }))

  useEffect(() => {
    if (textareaRef.current) {
      const el = textareaRef.current
      el.style.height = "auto"
      el.style.height = `${Math.min(el.scrollHeight, 320)}px`
    }
  }, [input])

  const handleSubmit = async (e?: React.FormEvent, promptText?: string) => {
    e?.preventDefault()

    const messageContent = promptText || input
    if (!messageContent.trim() || isLoading) return

    setInput("")
    onChatStart()

    await sendMessage(messageContent)
  }

  const handleComposerKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      void handleSubmit(undefined, undefined)
    }
  }

  const handleSuggestionClick = async (suggestion: string) => {
    if (isLoading) return
    
    onChatStart()
    await sendMessage(suggestion)
  }

  const syncConversationQuery = useCallback((nextId: string | null) => {
    const current = searchParams.get("conversationId")
    if (current === nextId || (!current && !nextId)) {
      return
    }

    const params = new URLSearchParams(searchParams.toString())
    params.set("tab", "chat")
    params.delete("searchTab")
    params.delete("new")

    if (nextId) {
      params.set("conversationId", nextId)
    } else {
      params.delete("conversationId")
    }

    router.replace(`/?${params.toString()}`, { scroll: false })
  }, [router, searchParams])

  const handleComposerNewConversation = useCallback(() => {
    startNewConversation()
    onConversationChange?.(null)
    syncConversationQuery(null)
    void getSuggestions()
  }, [getSuggestions, onConversationChange, startNewConversation, syncConversationQuery])

  useEffect(() => {
    if (activeConversationId && activeConversationId !== lastConversationRef.current) {
      lastConversationRef.current = activeConversationId
      onConversationChange?.(activeConversationId)
      syncConversationQuery(activeConversationId)
    }

    if (!activeConversationId && lastConversationRef.current !== null) {
      lastConversationRef.current = null
      onConversationChange?.(null)
      syncConversationQuery(null)
    }
  }, [activeConversationId, onConversationChange, syncConversationQuery])

  useEffect(() => {
    if (newChatTrigger === undefined) return
    if (newChatTrigger === newChatRef.current) return
    newChatRef.current = newChatTrigger
    handleComposerNewConversation()
  }, [handleComposerNewConversation, newChatTrigger])

  const formatMessageTimestamp = (value: Date | string) => {
    let date: Date | null = null

    if (value instanceof Date) {
      date = value
    } else if (typeof value === 'string') {
      const trimmed = value.trim()
      if (!trimmed) {
        return null
      }

      const hasTimezoneOffset = /([zZ]|[+-]\d{2}:?\d{2})$/.test(trimmed)
      const normalized = hasTimezoneOffset ? trimmed : `${trimmed.endsWith('Z') ? trimmed : `${trimmed}Z`}`

      const parsed = new Date(normalized)
      if (!Number.isNaN(parsed.getTime())) {
        date = parsed
      }
    }

    if (!date || Number.isNaN(date.getTime())) {
      return null
    }

    // Use formatRelative for chat bubbles (e.g., "2 hours ago", "yesterday")
    return formatRelative(date.toISOString())
  }

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header with trip info */}
      <div className="p-4 border-b border-gray-200 bg-white">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <span>� Trò chuyện với AI để lên kế hoạch du lịch</span>
        </div>
      </div>

      {/* Chat Messages */}
      <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-4">
        {/* Error Alert */}
        {error && (
          <Alert variant="destructive" className="mb-4">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {messages.map((message, index) => {
          const formattedTimestamp = formatMessageTimestamp(message.timestamp)
          const isUserMessage = message.isUser

          const isLastMessage = index === messages.length - 1
          const isCurrentlyStreaming = isLastMessage && !isUserMessage && isLoading

          return (
            <div key={message.id} className={`flex ${isUserMessage ? "justify-end" : "justify-start"}`}>
              <div className={cn("space-y-2", isUserMessage ? "max-w-[80%]" : "w-full max-w-full")}>
                {isUserMessage ? (
                  <div className="bg-blue-600 text-white rounded-2xl px-4 py-2">
                    <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                    {formattedTimestamp && (
                      <div className="mt-2 text-xs text-blue-100/80 text-right">
                        {formattedTimestamp}
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="bg-gray-100 rounded-2xl px-4 py-3 w-full">
                    <AiResponseRenderer
                      message={message.content}
                      results={message.results || []}
                      requiresConfirmation={message.requiresConfirmation}
                      confirmationContext={message.confirmationContext}
                      onFlightBook={onFlightBook}
                      onHotelBook={onHotelBook}
                      onLocationClick={onLocationClick}
                      onConfirm={handleConfirm}
                      onCancel={handleCancel}
                      canBook={true}
                      isLoading={isCurrentlyStreaming}
                    />

                    {formattedTimestamp && (!isCurrentlyStreaming || message.content) && (
                      <div className="mt-3 text-xs text-gray-500 text-left">
                        {formattedTimestamp}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )
        })}

        <div ref={messagesEndRef} />
      </div>

      {/* Message Input */}
      <div className="p-4 border-t border-gray-200 bg-white">
        <form
          onSubmit={handleSubmit}
          className="group/composer w-full space-y-3"
          style={{ viewTransitionName: "composer" }}
        >
          <div
            className={cn(
              "grid grid-cols-[auto_1fr_auto]",
              "bg-token-bg-primary shadow-short rounded-[28px] overflow-hidden bg-white",
              "border border-gray-200",
              "[grid-template-areas:'leading_primary_trailing']",
              "focus-within:ring-2 focus-within:ring-blue-500 focus-within:border-blue-500"
            )}
          >
            <div className="flex items-center justify-center px-2 py-1 [grid-area:leading]">
              <button
                type="button"
                className="composer-btn flex h-10 w-10 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700"
                onClick={handleComposerNewConversation}
                title="Bắt đầu cuộc trò chuyện mới"
              >
                <Plus className="h-5 w-5" />
              </button>
            </div>

            <div className="-my-2 flex min-h-14 items-center px-2 py-2 [grid-area:primary]">
              <div className="flex-1">
                <textarea
                  ref={(el) => {
                    textareaRef.current = el
                  }}
                  value={input}
                  onChange={(event) => {
                    setInput(event.target.value)
                    event.target.style.height = "auto"
                    event.target.style.height = `${Math.min(event.target.scrollHeight, 320)}px`
                  }}
                  onKeyDown={handleComposerKeyDown}
                  placeholder="Hỏi về địa điểm du lịch, khách sạn, chuyến bay..."
                  rows={1}
                  disabled={isLoading}
                  className={cn(
                    "w-full resize-none border-0 bg-transparent text-sm text-gray-800 placeholder:text-gray-400",
                    "focus-visible:outline-none focus-visible:ring-0",
                    "max-h-[320px] leading-6"
                  )}
                />
              </div>
            </div>

            <div className="flex items-center gap-1 pr-3 [grid-area:trailing]">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="composer-btn h-9 w-9 rounded-full text-gray-500 hover:text-gray-700 hover:bg-gray-100"
                disabled
                title="Voice input (coming soon)"
              >
                <Mic className="h-4 w-4" />
              </Button>
              <Button
                type="submit"
                variant="ghost"
                size="icon"
                className={cn(
                  "composer-btn ml-1 flex h-9 w-9 items-center justify-center rounded-full",
                  !input.trim() || isLoading
                    ? "text-gray-400"
                    : "bg-blue-500 text-white hover:bg-blue-600"
                )}
                disabled={isLoading || !input.trim()}
              >
                <Send className="h-4 w-4" />
              </Button>
          </div>
          </div>

          {currentSuggestions.length > 0 && (
            <div className="grid [grid-area:footer]">
              <div className="flex flex-wrap items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-2">
                <span className="text-xs font-medium text-gray-500">Gợi ý:</span>
                {currentSuggestions.map((suggestion, index) => (
                  <Button
                    key={`suggestion-${index}`}
                    variant="secondary"
                    size="sm"
                    className="h-7 rounded-full bg-white text-xs text-gray-600 hover:bg-blue-50 hover:text-blue-600"
                    onClick={() => handleSuggestionClick(suggestion)}
                    disabled={isLoading}
                  >
                    {suggestion}
                  </Button>
                ))}
              </div>
            </div>
          )}
        </form>
      </div>
    </div>
  )
})

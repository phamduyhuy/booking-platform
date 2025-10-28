"use client"

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { AuthClient, UserInfo } from '@/lib/auth-client'
import { aiChatService } from '@/modules/ai'
import type { ChatConversationSummary } from '@/modules/ai'

interface AuthContextType {
  user: UserInfo | null
  isLoading: boolean
  isAuthenticated: boolean
  login: () => void
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  chatConversations: ChatConversationSummary[]
  refreshChatConversations: () => Promise<void>
  upsertChatConversation: (conversation: Pick<ChatConversationSummary, "id" | "title"> & Partial<ChatConversationSummary>) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [chatConversations, setChatConversations] = useState<ChatConversationSummary[]>([])

  const loadChatConversations = useCallback(async (currentUser?: UserInfo | null) => {
    const resolvedUser = currentUser ?? user
    if (!resolvedUser) {
      setChatConversations([])
      return
    }

    try {
      const serverConversations = await aiChatService.listConversations()
      if (!serverConversations) {
        return
      }

      const sorted = [...serverConversations]
        .sort((a, b) => {
          const aDate = a.lastUpdated ?? a.createdAt ?? ''
          const bDate = b.lastUpdated ?? b.createdAt ?? ''
          return bDate.localeCompare(aDate)
        })
        .slice(0, 50)

      const summaries: ChatConversationSummary[] = sorted.map((conversation, index) => {
        const fallbackTitle = `Cuộc trò chuyện ${index + 1}`
        const normalizedTitle =
          conversation.title && conversation.title.trim().length > 0
            ? conversation.title.trim()
            : fallbackTitle
        return {
          id: conversation.id,
          title: normalizedTitle,
          createdAt: conversation.createdAt ?? new Date().toISOString(),
          lastUpdated: conversation.lastUpdated ?? conversation.createdAt ?? new Date().toISOString(),
        }
      })

      if (summaries.length === 0) {
        return
      }

      setChatConversations((prev) => {
        const merged = new Map<string, ChatConversationSummary>()

        prev.forEach((conversation) => {
          merged.set(conversation.id, conversation)
        })

        summaries.forEach((conversation) => {
          merged.set(conversation.id, conversation)
        })

        return Array.from(merged.values())
          .sort((a, b) => {
            const aDate = a.lastUpdated ?? a.createdAt ?? ''
            const bDate = b.lastUpdated ?? b.createdAt ?? ''
            return bDate.localeCompare(aDate)
          })
          .slice(0, 50)
      })
    } catch (error) {
      console.error('Failed to load chat conversations:', error)
      // Preserve existing conversations to avoid clearing sidebar on transient failures
      setChatConversations((prev) => prev)
    }
  }, []) // Remove user dependency to prevent infinite loop

  const refreshUser = useCallback(async () => {
    try {
      const userInfo = await AuthClient.getUserInfo()
      if (userInfo) {
        setUser(userInfo)
        await loadChatConversations(userInfo)
      } else {
        if (user) {
          console.warn('Failed to refresh user info; retaining existing session context.')
        } else {
          setUser(null)
          setChatConversations([])
        }
      }
    } catch (error) {
      console.error('Failed to fetch user info:', error)
      if (!user) {
        setUser(null)
        setChatConversations([])
      }
    } finally {
      setIsLoading(false)
    }
  }, [loadChatConversations, user])

  const login = () => {
    window.location.href = AuthClient.loginUrl()
  }

  const logout = async () => {
    try {
      await AuthClient.logout()
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      setUser(null)
      setChatConversations([])
    }
  }

  useEffect(() => {
    refreshUser()
  }, [refreshUser])

  const upsertChatConversation = useCallback((conversation: Pick<ChatConversationSummary, "id" | "title"> & Partial<ChatConversationSummary>) => {
    setChatConversations(prev => {
      const existing = prev.find(item => item.id === conversation.id)
      const createdAt = existing?.createdAt ?? conversation.createdAt ?? new Date().toISOString()
      const lastUpdated = conversation.lastUpdated ?? new Date().toISOString()
      const rawTitle = (conversation.title ?? existing?.title ?? "Cuộc trò chuyện").trim()
      const normalizedTitle = rawTitle.length > 0 ? rawTitle : "Cuộc trò chuyện"

      const updated: ChatConversationSummary = {
        id: conversation.id,
        title: normalizedTitle,
        createdAt,
        lastUpdated,
      }

      const others = prev.filter(item => item.id !== conversation.id)
      return [updated, ...others].slice(0, 50)
    })
  }, [])

  const refreshChatConversations = useCallback(() => loadChatConversations(), [loadChatConversations])

  const value: AuthContextType = {
    user,
    isLoading,
    isAuthenticated: !!user,
    login,
    logout,
    refreshUser,
    chatConversations,
    refreshChatConversations,
    upsertChatConversation,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

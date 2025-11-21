"use client"

import React, { Suspense } from "react"
import { Sidebar } from "@/components/sidebar"

interface AppShellProps {
  children: React.ReactNode
}

export function AppShell({ children }: AppShellProps) {
  return (
    <div className="flex h-full bg-background text-foreground">
      <Suspense
        fallback={
          <nav className="w-[72px] min-w-[72px] flex items-center justify-center border-r border-border text-xs text-muted-foreground">
            Loading…
          </nav>
        }
      >
        <Sidebar />
      </Suspense>
      <div className="flex flex-1 min-w-0 min-h-0 h-full">
        <div className="flex flex-1 min-w-0 min-h-0 h-full">
          {children}
        </div>
      </div>
    </div>
  )
}

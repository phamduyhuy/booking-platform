"use client"

import { Suspense } from "react"
import { SearchInterface } from "@/components/search-interface"

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full w-full items-center justify-center text-sm text-muted-foreground">
          Loading...
        </div>
      }
    >
      <SearchInterface />
    </Suspense>
  )
}

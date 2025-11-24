import React from "react";
import { cn } from "@/lib/utils";

/**
 * Skeleton loader for cards during loading
 */
export function CardSkeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "bg-white border border-gray-200 rounded-lg p-4 shadow-sm",
        className
      )}
    >
      <div className="skeleton-loading h-24 w-full rounded mb-3" />
      <div className="space-y-2">
        <div className="skeleton-loading h-3 w-3/4 rounded" />
        <div className="skeleton-loading h-3 w-1/2 rounded" />
        <div className="skeleton-loading h-3 w-2/3 rounded" />
      </div>
    </div>
  );
}

/**
 * Loading state for entire result section
 */
export function ResultsSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-3">
      <div className="skeleton-loading h-5 w-40 rounded mb-2" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: count }).map((_, i) => (
          <CardSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

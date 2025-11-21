"use client"

import { useState, useEffect } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Plane, Hotel } from "lucide-react"
import { cn } from "@/lib/utils"
import { FlightSearchTab } from "@/components/search/flight-search-tab"
import { HotelSearchTab } from "@/components/search/hotel-search-tab"
import { BookingModal } from "@/components/booking-modal"
import type { FlightSearchResult } from "@/modules/flight/type"
import type { HotelSearchResult } from "@/modules/hotel/type"
import type { DestinationSearchResult } from "@/types/common"

type SearchTab = "flights" | "hotels"

export function SearchInterface() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [activeTab, setActiveTab] = useState<SearchTab>("flights")
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false)
  


  // Handle URL parameters for searchTab
  useEffect(() => {
    const searchTab = searchParams.get("searchTab") as SearchTab
    
    if (searchTab && (searchTab === "flights" || searchTab === "hotels")) {
      setActiveTab(searchTab)
    } else {
      // Default to flights if no searchTab or invalid searchTab
      setActiveTab("flights")
      // Update URL to include default searchTab
      const params = new URLSearchParams(searchParams.toString())
      params.set("searchTab", "flights")
      router.replace(`/search?${params.toString()}`, { scroll: false })
    }
  }, [searchParams, router])

  const handleSearchTabChange = (tab: SearchTab) => {
    setActiveTab(tab)
    // Update URL without refreshing the page
    const params = new URLSearchParams(searchParams.toString())
    params.set("searchTab", tab)
    
    router.replace(`/search?${params.toString()}`, { scroll: false })
  }




  // Handler to open booking modal instead of navigating to /bookings page
  const handleOpenBookingModal = () => {
    setIsBookingModalOpen(true)
  }

  const searchTabs = [
    { id: "flights" as const, label: "Flights", icon: Plane },
    { id: "hotels" as const, label: "Stays", icon: Hotel },
  ]

  return (
    <div className="flex flex-col h-full">
      {/* Search Tab Navigation */}
      <div className="flex items-center p-4 bg-background border-b">
        {/* Search Type Tabs */}
        <div className="flex bg-muted rounded-lg p-1">
          {searchTabs.map((tab) => {
            const Icon = tab.icon
            return (
              <button
                key={tab.id}
                onClick={() => handleSearchTabChange(tab.id)}
                className={cn(
                  "flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-all duration-200",
                  activeTab === tab.id
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                <Icon className="h-4 w-4" />
                {tab.label}
              </button>
            )
          })}
        </div>
      </div>

      {/* Search Content */}
      <div className="flex-1 overflow-hidden bg-background">
        {activeTab === "flights" && (
          <FlightSearchTab onBookingStart={handleOpenBookingModal} />
        )}
        {activeTab === "hotels" && (
          <HotelSearchTab onBookingStart={handleOpenBookingModal} />
        )}
      </div>

      {/* Booking Modal */}
      <BookingModal 
        open={isBookingModalOpen} 
        onOpenChange={setIsBookingModalOpen} 
      />
    </div>
  )
}

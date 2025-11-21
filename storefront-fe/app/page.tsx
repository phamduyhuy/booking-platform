"use client"

import { useState, useEffect, Suspense, useCallback } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Search, MessageCircle } from "lucide-react"
import { cn } from "@/lib/utils"
import { ChatInterface } from "@/components/chat-interface"
import { BookingModal } from "@/components/booking-modal"
import { RecommendPanel } from "@/components/recommend-panel"
import { useBooking } from "@/contexts/booking-context"
import { useRecommendPanel } from "@/contexts/recommend-panel-context"
import HotelDetailsModal from "@/modules/hotel/component/HotelDetailsModal"


function HomePageContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [newChatSignal, setNewChatSignal] = useState(0)
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false)
  const [selectedHotelForDetails, setSelectedHotelForDetails] = useState<{ hotelId: string | null; checkInDate?: string; checkOutDate?: string; guestCount?: number; roomCount?: number }>({ hotelId: null })
  const { results, setResults: setRecommendResults, clearResults: clearRecommendResults, showLocation } = useRecommendPanel()
  
  const {
    resetBooking, 
    setBookingType, 
    setSelectedFlight, 
    setSelectedHotel, 
    updateBookingData, 
    setStep,
    resumeBooking,
    selectedFlight,
    selectedHotel,
    setFlightDetails,
    setHotelDetails,
  } = useBooking()

  // Handle URL parameters
  useEffect(() => {

    const newChatParam = searchParams.get("new")
    if (newChatParam === "1") {
      setNewChatSignal((prev) => prev + 1)
      setConversationId(null)
      clearRecommendResults()

      const params = new URLSearchParams(searchParams.toString())
      params.delete("new")
      params.delete("conversationId")
      router.replace(`/?${params.toString()}`, { scroll: false })
      return
    }

    const resumeBookingId = searchParams.get("resume")
    if (resumeBookingId) {
      const stored = sessionStorage.getItem('bookingResumePayload')
      const paramsClone = new URLSearchParams(searchParams.toString())
      paramsClone.delete("resume")
      router.replace(paramsClone.toString() ? `/?${paramsClone.toString()}` : '/', { scroll: false })

      if (stored) {
        try {
          const payload = JSON.parse(stored)
          void resumeBooking(payload).then(() => {
            setIsBookingModalOpen(true)
          })
        } catch (error) {
          console.error('Failed to resume booking from history', error)
        } finally {
          sessionStorage.removeItem('bookingResumePayload')
        }
      }
      return
    }

    const conversation = searchParams.get("conversationId")
    setConversationId(conversation)
  }, [router, searchParams, clearRecommendResults, resumeBooking])

  const handleFlightBook = (flight: any) => {
    const hasHotelSelected = Boolean(selectedHotel)
    const currency = flight.currency || 'VND'

    if (hasHotelSelected) {
      setBookingType('both')
      setFlightDetails(null)
      updateBookingData({
        bookingType: 'COMBO',
        totalAmount: 0,
        currency,
        flightSelection: undefined,
      })
    } else {
      resetBooking()
      setBookingType('flight')
      setFlightDetails(null)
      updateBookingData({
        bookingType: 'FLIGHT',
        totalAmount: 0,
        currency,
        flightSelection: undefined,
        hotelSelection: undefined,
        comboDiscount: undefined,
      })
    }

    console.log('✈️ Booking flight:', flight)
    setSelectedFlight({
      flightId: flight.flightId || flight.id,
      flightNumber: flight.flightNumber,
      airline: flight.airline,
      origin: flight.origin,
      destination: flight.destination,
      originLatitude: flight.originLatitude ?? flight.raw?.originLatitude,
      originLongitude: flight.originLongitude ?? flight.raw?.originLongitude,
      destinationLatitude: flight.destinationLatitude ?? flight.raw?.destinationLatitude,
      destinationLongitude: flight.destinationLongitude ?? flight.raw?.destinationLongitude,
      departureTime: flight.departureTime ?? flight.raw?.departureTime ?? flight.raw?.departure ?? null,
      arrivalTime: flight.arrivalTime,
      duration: flight.duration,
      price: flight.price,
      currency,
      seatClass: flight.seatClass,
      logo: flight.logo?? flight.raw?.airlineLogo ?? flight.imageUrl ?? null,
      scheduleId: flight.scheduleId,
      fareId: flight.fareId,
    })
    
    // Set step to passengers
    setStep('passengers')
    
    // Open booking modal
    setIsBookingModalOpen(true)
  }

  const handleHotelBook = (hotel: any, room?: any) => {
    if (!room) {
      setSelectedHotelForDetails({
        hotelId: hotel.id || hotel.hotelId,
        checkInDate: hotel.checkInDate,
        checkOutDate: hotel.checkOutDate,
        guestCount: hotel.guests,
        roomCount: hotel.rooms
      })
      return
    }

    const hasFlightSelected = Boolean(selectedFlight)
    const currency = hotel.currency || 'VND'

    if (hasFlightSelected) {
      setBookingType('both')
      setHotelDetails(null)
      updateBookingData({
        bookingType: 'COMBO',
        totalAmount: 0,
        currency,
        hotelSelection: undefined,
      })
    } else {
      resetBooking()
      setBookingType('hotel')
      setHotelDetails(null)
      updateBookingData({
        bookingType: 'HOTEL',
        totalAmount: 0,
        currency,
        flightSelection: undefined,
        hotelSelection: undefined,
        comboDiscount: undefined,
      })
    }
    
    setSelectedHotel({
      id: selectedHotelForDetails.hotelId || hotel.id || hotel.hotelId,
      name: hotel.name,
      address: hotel.address,
      city: hotel.city,
      country: hotel.country,
      hotelLatitude: hotel.hotelLatitude ?? hotel.latitude ?? hotel.location?.latitude,
      hotelLongitude: hotel.hotelLongitude ?? hotel.longitude ?? hotel.location?.longitude,
      rating: hotel.rating,
      roomType: room.type,
      roomName: room.name,
      price: room.price,
      pricePerNight: room.price,
      totalPrice: room.price * (hotel.rooms ?? 1) * (hotel.nights ?? 1),
      currency,
      amenities: room.amenities,
      image: hotel.image,
      checkInDate: hotel.checkInDate,
      checkOutDate: hotel.checkOutDate,
      guests: hotel.guests,
      rooms: hotel.rooms,
      nights: hotel.nights,
    })

    setStep('passengers')
    setIsBookingModalOpen(true)
  }

  const handleLocationClick = useCallback((location: { 
    lat: number; 
    lng: number; 
    title: string; 
    description?: string 
  }) => {
    console.log('📍 Location clicked:', location)
    showLocation(location)
  }, [showLocation])

  const handleSearchResults = useCallback((results: any[], type: string) => {
    console.log('🔍 Search results:', results, type)
    setRecommendResults(Array.isArray(results) ? results : [])
  }, [setRecommendResults])

  const handleConversationChange = useCallback((id: string | null) => {
    setConversationId(id)
  }, [])



  return (
    <>
      <div className="flex flex-1 min-h-0 min-w-0 h-full">
        <main className="flex-1 flex flex-col h-full min-w-0 min-h-0 overflow-hidden">
          {/* Tab Content */}
          <div className="flex-1 min-h-0 overflow-hidden">
            <ChatInterface
              onSearchResults={handleSearchResults}
              onStartBooking={() => {}}
              onChatStart={() => {}}
              conversationId={conversationId}
              onConversationChange={handleConversationChange}
              newChatTrigger={newChatSignal}
              onFlightBook={handleFlightBook}
              onHotelBook={handleHotelBook}
              onLocationClick={handleLocationClick}
            />
          </div>
        </main>
        <aside className="hidden md:flex h-full border-l border-border flex-col overflow-hidden shrink-0 bg-background md:w-[320px]">
          <RecommendPanel results={results} className="w-full" />
        </aside>
      </div>

      {/* Booking Modal */}
      <BookingModal 
        open={isBookingModalOpen} 
        onOpenChange={setIsBookingModalOpen}
      />

      {/* Hotel Details Modal */}
      <HotelDetailsModal
        hotelId={selectedHotelForDetails.hotelId}
        isOpen={!!selectedHotelForDetails.hotelId}
        onClose={() => setSelectedHotelForDetails({ hotelId: null })}
        onBookRoom={(payload) => {
          // Handle booking room from hotel details modal
          handleHotelBook(payload.hotel, payload.room)
        }}
        checkInDate={selectedHotelForDetails.checkInDate}
        checkOutDate={selectedHotelForDetails.checkOutDate}
        guestCount={selectedHotelForDetails.guestCount}
        roomCount={selectedHotelForDetails.roomCount}
        canBook={true}
      />
    </>
  )
}

export default function HomePage() {
  return (
    <Suspense 
      fallback={
        <div className="flex h-full w-full items-center justify-center text-sm text-muted-foreground">
          Loading...
        </div>
      }
    >
      <HomePageContent />
    </Suspense>
  )
}

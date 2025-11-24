"use client"

import { useCallback, useEffect, useState, useRef } from "react"
import { useRouter } from "next/navigation"
import { HotelCardSkeleton } from "@/modules/hotel/component/HotelCardSkeleton"
import { HotelCard } from "@/components/cards"
import { Search, Filter, Building2, Calendar, Users, Star, Wifi, Car, Coffee, Dumbbell, ChevronUp, ChevronDown } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Slider } from "@/components/ui/slider"
import { cn } from "@/lib/utils"
import { hotelService } from "@/modules/hotel/service"
import type { InitialHotelData, HotelDetails, HotelSearchResult as ApiHotelSearchResult } from "@/modules/hotel/type"
import HotelDetailsModal from "@/modules/hotel/component/HotelDetailsModal"
import { HotelDestinationModal } from "@/modules/hotel/component/HotelDestinationModal"
import { formatPrice } from "@/lib/currency"
import { useBooking } from "@/contexts/booking-context"
import type { DestinationSearchResult } from "@/types"

interface HotelSearchResult {
  id: string;
  name: string;
  image: string;
  location: string;
  rating: number;
  reviews: number;
  price: number;
  originalPrice: number;
  amenities: string[];
  description: string;
}

interface HotelSearchTabProps {
  onBookingStart?: () => void
}

export function HotelSearchTab({ onBookingStart }: HotelSearchTabProps = {}) {
  const router = useRouter()
  const {
    resetBooking,
    setBookingType,
    setSelectedHotel,
    updateBookingData,
    setStep,
    selectedFlight,
    setHotelDetails,
  } = useBooking()
  
  // Filter states
  const [priceRange, setPriceRange] = useState([0, 5000000])
  const [selectedAmenities, setSelectedAmenities] = useState<string[]>([])
  const [selectedRatings, setSelectedRatings] = useState<number[]>([])
  const [sortBy, setSortBy] = useState("price-low")
  
  // Search states
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(1)
  const [limit] = useState(20)
  const [hasMore, setHasMore] = useState(false)
  const [results, setResults] = useState<HotelSearchResult[]>([])
  const [initialData, setInitialData] = useState<InitialHotelData | null>(null)
  const [hasSearched, setHasSearched] = useState(false)

  // Search form states
  const [destination, setDestination] = useState("")
  const [checkInDate, setCheckInDate] = useState("")
  const [checkOutDate, setCheckOutDate] = useState("")
  const [guests, setGuests] = useState("2-1") // format: guests-rooms

  // Modal states
  const [selectedHotelId, setSelectedHotelId] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isDestinationModalOpen, setIsDestinationModalOpen] = useState(false)

  // Collapse state for search form
  const [isSearchCollapsed, setIsSearchCollapsed] = useState(false)
  const [lastScrollY, setLastScrollY] = useState(0)

  const searchSectionRef = useRef<HTMLDivElement | null>(null)
  const resultsContainerRef = useRef<HTMLDivElement | null>(null)
  const isLoadingInitialData = useRef(false)

  // Defaults
  const DEFAULT_GUESTS = "2-1"

  const amenities = [
    { name: "WiFi miễn phí", icon: Wifi },
    { name: "Bãi đỗ xe", icon: Car },
    { name: "Nhà hàng", icon: Coffee },
    { name: "Phòng gym", icon: Dumbbell },
    { name: "Hồ bơi", icon: Building2 },
    { name: "Spa", icon: Building2 },
  ]

  const toggleAmenity = (amenity: string) => {
    setSelectedAmenities((prev) => (prev.includes(amenity) ? prev.filter((a) => a !== amenity) : [...prev, amenity]))
  }

  const toggleRating = (rating: number) => {
    setSelectedRatings((prev) => (prev.includes(rating) ? prev.filter((r) => r !== rating) : [...prev, rating]))
  }

  const scrollToSearch = () => {
    searchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const handleViewDetails = (hotel: HotelSearchResult) => {
    setSelectedHotelId(hotel.id)
    setIsModalOpen(true)
  }

  const handleBookNow = (hotel: HotelSearchResult) => {
    if (!hasSearched) {
      scrollToSearch()
      return
    }
    setSelectedHotelId(hotel.id)
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setSelectedHotelId(null)
  }

  const handleDestinationSelect = (destination: DestinationSearchResult) => {
    setDestination(destination.name)
  }

  const handleRoomBooking = ({ hotel, room, checkInDate, checkOutDate }: { hotel: HotelDetails; room: { id: string, name: string, features: string[], image: string, roomTypeId: string, roomId: string, roomType: string, price: number }; checkInDate?: string; checkOutDate?: string }) => {
    if (!hasSearched) {
      scrollToSearch()
      return
    }
    if (!hotel || !room) {
      console.error('Unable to start booking flow: missing hotel or room details')
      return
    }

    const hotelId = hotel.hotelId || selectedHotelId

    if (!hotelId) {
      console.error('Unable to start booking flow: missing hotel identifier')
      return
    }

    const amenities = Array.isArray(room.features) && room.features.length > 0
      ? room.features
      : Array.isArray(hotel.amenities)
      ? hotel.amenities
      : []

    const roomTypeId = (room as any)?.roomTypeId || room.id || room.roomId
    const roomId = room.roomId || room.id || roomTypeId
    const roomName = room.name || room.roomType || 'Selected Room'
    const roomType = room.roomType || room.name || 'Room'
    const price = Number(room.price ?? hotel.pricePerNight) || 0
    const [guestCountRaw, roomCountRaw] = guests.split('-')
    const guestCount = parseInt(guestCountRaw || '0', 10) || undefined
    const roomCount = parseInt(roomCountRaw || '0', 10) || undefined
    const nights = checkInDate && checkOutDate
      ? Math.max(1, Math.round((new Date(checkOutDate).getTime() - new Date(checkInDate).getTime()) / (1000 * 60 * 60 * 24)))
      : undefined
    const totalPrice = price * (roomCount ?? 1) * (nights ?? 1)
    const hasFlightSelection = Boolean(selectedFlight)

    if (hasFlightSelection) {
      setBookingType('both')
      setHotelDetails(null)
      updateBookingData({
        bookingType: 'COMBO',
        totalAmount: 0,
        currency: hotel.currency || 'VND',
        hotelSelection: undefined,
      })
    } else {
      resetBooking()
      setBookingType('hotel')
      setHotelDetails(null)
      updateBookingData({
        bookingType: 'HOTEL',
        totalAmount: 0,
        currency: hotel.currency || 'VND',
        flightSelection: undefined,
        hotelSelection: undefined,
        comboDiscount: undefined,
      })
    }

    setSelectedHotel({
      id: hotelId,
      name: hotel.name,
      address: hotel.address || '',
      city: hotel.city || '',
      country: hotel.country || '',
      hotelLatitude: hotel.latitude,
      hotelLongitude: hotel.longitude,
      rating: hotel.starRating ?? hotel.rating,
      roomTypeId: roomTypeId ? String(roomTypeId) : '',
      roomId: roomId,
      roomType,
      roomName,
      price,
      pricePerNight: price,
      totalPrice,
      currency: hotel.currency || 'VND',
      amenities,
      image: room.image || hotel.primaryImage || hotel.images?.[0],
      images: hotel.images,
      roomImages: room.image ? [room.image] : hotel.images,
      checkInDate: checkInDate || undefined,
      checkOutDate: checkOutDate || undefined,
      guests: guestCount,
      rooms: roomCount,
      nights,
    })
    setStep('passengers')
    handleCloseModal()
    
    // Use callback if provided (for modal), otherwise navigate to booking page
    if (onBookingStart) {
      onBookingStart()
    } else {
      router.push('/bookings')
    }
  }

  const loadInitialData = useCallback(async () => {
    if (isLoadingInitialData.current) {
      return
    }

    isLoadingInitialData.current = true
    setLoading(true)
    setError(null)
    try {
      const res = await hotelService.search({
        destination: "",
        checkInDate: "",
        checkOutDate: "",
        guests: 2,
        rooms: 1,
        page: 1,
        limit,
      })
      setInitialData(res as InitialHotelData)
      
      const ui = (res.hotels || []).map((h: ApiHotelSearchResult) => ({
        id: h.hotelId,
        name: h.name,
        image: h.primaryImage || h.images?.[0] || "/placeholder.svg",
        location: `${h.city || ""}${h.city ? ", " : ""}Vietnam`,
        rating: h.rating || 0,
        reviews: 0,
        price: h.pricePerNight || 0,
        originalPrice: Math.round((h.pricePerNight || 0) * 1.2),
        amenities: h.amenities || [],
        description: "",
      }))
      setResults(ui)
      setHasMore(Boolean(res.hasMore))
    } catch (e: any) {
      setError(e?.message || "Failed to load initial hotel data")
    } finally {
      setLoading(false)
      isLoadingInitialData.current = false
    }
  }, [limit])

  async function handleSearch(nextPage?: number) {
    if (!destination.trim() || !checkInDate || !checkOutDate) {
      setError('Vui lòng chọn điểm đến và ngày nhận/trả phòng để xem giá')
      scrollToSearch()
      return
    }

    const checkIn = new Date(checkInDate)
    const checkOut = new Date(checkOutDate)
    if (Number.isNaN(checkIn.getTime()) || Number.isNaN(checkOut.getTime())) {
      setError('Ngày nhận phòng / trả phòng không hợp lệ')
      scrollToSearch()
      return
    }

    if (checkOut <= checkIn) {
      setError('Ngày trả phòng phải sau ngày nhận phòng')
      scrollToSearch()
      return
    }

    const [guestCountRaw, roomCountRaw] = guests.split('-')
    const guestCount = parseInt(guestCountRaw || '0', 10)
    const roomCount = parseInt(roomCountRaw || '0', 10)
    if (!Number.isFinite(guestCount) || guestCount <= 0 || !Number.isFinite(roomCount) || roomCount <= 0) {
      setError('Vui lòng chọn số khách và số phòng hợp lệ')
      scrollToSearch()
      return
    }

    setLoading(true)
    setError(null)
    try {
      const usePage = nextPage ?? page
      const [g, r] = guests.split("-")
      const res = await hotelService.search({
        destination,
        checkInDate,
        checkOutDate,
        guests: parseInt(g || "2", 10),
        rooms: parseInt(r || "1", 10),
        page: usePage,
        limit,
      })
      const ui = (res.hotels || []).map((h: ApiHotelSearchResult) => ({
        id: h.hotelId,
        name: h.name,
        image: h.primaryImage || h.images?.[0] || "/placeholder.svg",
        location: `${h.city || ""}${h.city ? ", " : ""}Vietnam`,
        rating: h.rating || 0,
        reviews: 0,
        price: h.pricePerNight || 0,
        originalPrice: Math.round((h.pricePerNight || 0) * 1.2),
        amenities: h.amenities || [],
        description: "",
      }))
      setResults(ui)
      setHasMore(Boolean(res.hasMore))
      setPage(usePage)
      setHasSearched(true)
    } catch (e: any) {
      setError(e?.message || "Failed to load hotels")
    } finally {
      setLoading(false)
    }
  }

  function nextPage() {
    if (hasMore) {
      const p = page + 1
      void handleSearch(p)
    }
  }

  function prevPage() {
    if (page > 1) {
      const p = page - 1
      void handleSearch(p)
    }
  }

  // Initialize with default data
  useEffect(() => {
    if (results.length === 0 && !loading && !initialData) {
      void loadInitialData()
    }
  }, [results.length, loading, initialData, loadInitialData])

  // Handle scroll for search form collapse
  useEffect(() => {
    const handleScroll = () => {
      if (!resultsContainerRef.current) return
      
      const currentScrollY = resultsContainerRef.current.scrollTop
      
      if (currentScrollY < 10) {
        // At top of results - always expand
        setIsSearchCollapsed(false)
      } else if (currentScrollY > lastScrollY && currentScrollY > 100) {
        // Scrolling down and past 100px - collapse
        setIsSearchCollapsed(true)
      } else if (currentScrollY < lastScrollY) {
        // Scrolling up - expand
        setIsSearchCollapsed(false)
      }
      
      setLastScrollY(currentScrollY)
    }

    const container = resultsContainerRef.current
    if (container) {
      container.addEventListener('scroll', handleScroll, { passive: true })
      return () => container.removeEventListener('scroll', handleScroll)
    }
  }, [lastScrollY])

  const toggleSearchCollapse = () => {
    setIsSearchCollapsed(!isSearchCollapsed)
  }

  const [guestCountRaw, roomCountRaw] = guests.split('-')
  const selectedGuestCount = parseInt(guestCountRaw || '2', 10) || undefined
  const selectedRoomCount = parseInt(roomCountRaw || '1', 10) || undefined

  return (
    <div className="flex flex-col h-full bg-gray-50">
      {/* Search Form */}
      <div className="bg-white border-b shadow-sm" ref={searchSectionRef}>
        <div className="w-full">
          {/* Chevron Toggle Button - Always visible */}
          <div className="flex justify-center pt-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={toggleSearchCollapse}
              className="h-6 w-8 p-0 hover:bg-gray-100 rounded-full"
            >
              {isSearchCollapsed ? (
                <ChevronDown className="h-4 w-4 text-gray-600" />
              ) : (
                <ChevronUp className="h-4 w-4 text-gray-600" />
              )}
            </Button>
          </div>

          {/* Search Form Content - Collapsible */}
          <div 
            className={cn(
              "transition-all duration-300 ease-in-out overflow-hidden",
              isSearchCollapsed 
                ? "max-h-0 opacity-0" 
                : "max-h-96 opacity-100 pb-6"
            )}
          >
            <div className="px-6">
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Điểm đến</label>
                  <div className="flex gap-2">
                    <Input
                      value={destination}
                      onChange={(e) => setDestination(e.target.value)}
                      placeholder="Nhập thành phố, khách sạn hoặc địa chỉ..."
                      className="h-8 border-2 text-sm"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      className="h-8 border-2 text-sm whitespace-nowrap"
                      onClick={() => setIsDestinationModalOpen(true)}
                    >
                      Gợi ý
                    </Button>
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Nhận phòng</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-3 w-3 text-gray-400" />
                    <Input 
                      type="date" 
                      className="pl-8 h-8 border-2 text-sm" 
                      value={checkInDate} 
                      onChange={(e) => setCheckInDate(e.target.value)} 
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Trả phòng</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-3 w-3 text-gray-400" />
                    <Input 
                      type="date" 
                      className="pl-8 h-8 border-2 text-sm" 
                      value={checkOutDate} 
                      onChange={(e) => setCheckOutDate(e.target.value)} 
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Khách</label>
                  <Select value={guests} onValueChange={setGuests}>
                    <SelectTrigger className="h-8 border-2 text-sm">
                      <Users className="h-3 w-3 mr-2" />
                      <SelectValue placeholder="2 Khách, 1 Phòng" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="1-1">1 Khách, 1 Phòng</SelectItem>
                      <SelectItem value="2-1">2 Khách, 1 Phòng</SelectItem>
                      <SelectItem value="3-1">3 Khách, 1 Phòng</SelectItem>
                      <SelectItem value="4-1">4 Khách, 1 Phòng</SelectItem>
                      <SelectItem value="2-2">2 Khách, 2 Phòng</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex justify-end">
                <Button 
                  className="w-full md:w-auto h-8 bg-blue-600 text-white hover:bg-blue-700 text-sm font-medium px-8" 
                  onClick={() => handleSearch()} 
                  disabled={loading}
                >
                  <Search className="h-3 w-3 mr-2" />
                  {loading ? "Đang tìm kiếm..." : "Tìm khách sạn"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Results Section */}
      <div className="flex-1 overflow-auto" ref={resultsContainerRef}>
        <div className="w-full p-6">
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* Filters Sidebar - Sticky */}
            <div className="lg:col-span-1">
              <div className="sticky top-0 space-y-4">
                <Card className="max-h-[calc(100vh-120px)] overflow-hidden flex flex-col">
                  <CardHeader className="shrink-0">
                    <CardTitle className="flex items-center gap-2">
                      <Filter className="h-5 w-5" />
                      Bộ lọc
                    </CardTitle>
                  </CardHeader>
                <CardContent className="space-y-6 overflow-y-auto flex-1 pr-3">
                  {/* Price Range */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Giá mỗi đêm (VND)</label>
                    <Slider 
                      value={priceRange} 
                      onValueChange={setPriceRange} 
                      max={5000000} 
                      step={100000} 
                      className="mb-2" 
                    />
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <span>{formatPrice(priceRange[0])}</span>
                      <span>{formatPrice(priceRange[1])}</span>
                    </div>
                  </div>

                  {/* Star Rating */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Xếp hạng sao</label>
                    <div className="space-y-2">
                      {[5, 4, 3, 2, 1].map((rating) => (
                        <label key={rating} className="flex items-center space-x-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedRatings.includes(rating)}
                            onChange={() => toggleRating(rating)}
                            className="rounded border-gray-300"
                          />
                          <div className="flex items-center space-x-1">
                            {Array.from({ length: rating }).map((_, i) => (
                              <Star key={i} className="h-4 w-4 fill-yellow-400 text-yellow-400" />
                            ))}
                            <span className="text-sm ml-1">
                              {rating} Sao
                            </span>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>

                  {/* Amenities */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Tiện nghi</label>
                    <div className="space-y-2">
                      {amenities.map((amenity) => (
                        <label key={amenity.name} className="flex items-center space-x-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedAmenities.includes(amenity.name)}
                            onChange={() => toggleAmenity(amenity.name)}
                            className="rounded border-gray-300"
                          />
                          <amenity.icon className="h-4 w-4" />
                          <span className="text-sm">{amenity.name}</span>
                        </label>
                      ))}
                    </div>
                  </div>

                  {/* Property Type */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Loại chỗ ở</label>
                    <div className="space-y-2">
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input type="checkbox" className="rounded border-gray-300" />
                        <span className="text-sm">Khách sạn</span>
                      </label>
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input type="checkbox" className="rounded border-gray-300" />
                        <span className="text-sm">Khu nghỉ dưỡng</span>
                      </label>
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input type="checkbox" className="rounded border-gray-300" />
                        <span className="text-sm">Căn hộ</span>
                      </label>
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input type="checkbox" className="rounded border-gray-300" />
                        <span className="text-sm">Biệt thự</span>
                      </label>
                    </div>
                  </div>
                </CardContent>
              </Card>
              </div>
            </div>

            {/* Results */}
            <div className="lg:col-span-3">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-semibold">Kết quả tìm kiếm khách sạn</h2>
                <div className="flex items-center gap-4">
                  <span className="text-sm text-muted-foreground">
                    {initialData ? `${initialData.totalCount || results.length} khách sạn` : `${results.length} khách sạn`}
                  </span>
                  <Select value={sortBy} onValueChange={(value) => {
                    setSortBy(value)
                    setTimeout(() => void handleSearch(), 0)
                  }}>
                    <SelectTrigger className="w-40">
                      <SelectValue placeholder="Sắp xếp theo giá" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="price-low">Giá: Thấp đến cao</SelectItem>
                      <SelectItem value="price-high">Giá: Cao đến thấp</SelectItem>
                      <SelectItem value="rating">Đánh giá khách</SelectItem>
                      <SelectItem value="distance">Khoảng cách</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {error && (
                <Card className="mb-6">
                  <CardContent className="p-4 text-sm text-destructive-foreground">{error}</CardContent>
                </Card>
              )}

              <div className="space-y-4">
                {/* Pagination Controls */}
                <div className="flex items-center justify-between">
                  <div className="text-sm text-muted-foreground">Trang {page}</div>
                  <div className="flex gap-2">
                    <Button 
                      variant="outline" 
                      onClick={prevPage} 
                      disabled={loading || page === 1}
                    >
                      Trước
                    </Button>
                    <Button 
                      variant="outline" 
                      onClick={nextPage} 
                      disabled={loading || !hasMore}
                    >
                      Tiếp
                    </Button>
                  </div>
                </div>

                {/* Loading State */}
                {loading && (
                  <div className="space-y-3">
                    {Array.from({ length: 3 }).map((_, i) => (
                      <HotelCardSkeleton key={i} />
                    ))}
                  </div>
                )}

                {/* Hotel Results */}
                {results.map((hotel) => (
                  <HotelCard
                    key={hotel.id}
                    hotel={hotel}
                    onViewDetails={handleViewDetails}
                    onBook={handleBookNow}
                    showBookButton={hasSearched}
                  />
                ))}

                {/* Empty State */}
                {!loading && results.length === 0 && !error && (
                  <div className="text-center py-20">
                    <div className="text-6xl mb-4">🏨</div>
                    <h2 className="text-2xl font-semibold mb-2">
                      {hasSearched ? "Không tìm thấy khách sạn" : "Hãy bắt đầu tìm khách sạn phù hợp"}
                    </h2>
                    <p className="text-muted-foreground">
                      {hasSearched
                        ? "Thử thay đổi bộ lọc hoặc chọn ngày lưu trú khác."
                        : "Vui lòng nhập thông tin tìm kiếm để xem danh sách khách sạn phù hợp."
                      }
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Modals */}
      <HotelDetailsModal
        hotelId={selectedHotelId}
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        onBookRoom={handleRoomBooking}
        canBook={hasSearched}
        onPromptSearch={scrollToSearch}
        checkInDate={checkInDate || undefined}
        checkOutDate={checkOutDate || undefined}
        guestCount={selectedGuestCount}
        roomCount={selectedRoomCount}
      />

      <HotelDestinationModal
        isOpen={isDestinationModalOpen}
        onClose={() => setIsDestinationModalOpen(false)}
        onSelect={(destination: any) => {
          setDestination(destination.name)
          setIsDestinationModalOpen(false)
        }}
        title="Chọn điểm đến"
        placeholder="Tìm kiếm thành phố hoặc khách sạn..."
      />
    </div>
  )
}

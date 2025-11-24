"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { ArrowRight, Search, Calendar, Users, Filter, Plane, Star, ChevronUp, ChevronDown } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Slider } from "@/components/ui/slider"
import { cn } from "@/lib/utils"
import { useDateFormatter } from "@/hooks/use-date-formatter"
import { formatPrice } from "@/lib/currency"
import { useBooking } from "@/contexts/booking-context"
import { flightService } from "@/modules/flight/service"
import type { InitialFlightData, FareClass, FlightDetails } from "@/modules/flight/type"
import { FlightCardSkeleton } from "@/modules/flight/component/FlightCardSkeleton"
import FlightDetailsModal from "@/modules/flight/component/FlightDetailsModal"
import { FlightDestinationModal } from "@/modules/flight/component/FlightDestinationModal"
import { FlightCard } from "@/components/cards"

interface City {
  code: string
  name: string
  type: string
}

interface FlightSearchResult {
  id: string;
  airline: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  departureDateTime?: string;
  arrivalDateTime?: string;
  currency: string;
  seatClass: string;
  logo: string;
  scheduleId?: string;
  fareId?: string;
  departure: {
    time: string;
    airport: string;
    city: string;
  };
  arrival: {
    time: string;
    airport: string;
    city: string;
  };
  duration: string;
  stops: string;
  price: number;
  class: string;
  rating: number;
  raw: any; // Keep raw as any for now
}

interface ApiFlight {
  flightId: string;
  airline: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  departureDateTime?: string;
  arrivalDateTime?: string;
  currency?: string;
  seatClass?: string;
  airlineLogo?: string;
  scheduleId?: string;
  fareId?: string;
  duration?: string;
  stops?: number;
  price: number;
}

interface FlightSearchTabProps {
  onBookingStart?: () => void
}

export function FlightSearchTab({ onBookingStart }: FlightSearchTabProps = {}) {
  const router = useRouter()
  const { formatTimeOnly } = useDateFormatter()

  const {
    resetBooking,
    setBookingType,
    updateBookingData,
    setSelectedFlight,
    setStep,
    selectedHotel,
    setFlightDetails,
  } = useBooking()
  
  // Search form state
  const [origin, setOrigin] = useState<City | null>(null)
  const [destination, setDestination] = useState<City | null>(null)
  const [departDate, setDepartDate] = useState("")
  const [returnDate, setReturnDate] = useState("")
  const [passengers, setPassengers] = useState("1")
  const [seatClass, setSeatClass] = useState<FareClass>("ECONOMY")

  // Filter states
  const [priceRange, setPriceRange] = useState([0, 5000000])
  const [durationRange, setDurationRange] = useState([0, 24])
  const [selectedAirlines, setSelectedAirlines] = useState<string[]>([])
  const [sortBy, setSortBy] = useState("departure")

  // Search states
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(1)
  const [limit] = useState(20)
  const [hasMore, setHasMore] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)

  // Results
  const [flightResults, setFlightResults] = useState<FlightSearchResult[]>([])
  const [initialData, setInitialData] = useState<InitialFlightData | null>(null)

  // Modals
  const [selectedFlightForModal, setSelectedFlightForModal] = useState<FlightSearchResult | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isOriginModalOpen, setIsOriginModalOpen] = useState(false)
  const [isDestinationModalOpen, setIsDestinationModalOpen] = useState(false)

  // Collapse state for search form
  const [isSearchCollapsed, setIsSearchCollapsed] = useState(false)
  const [lastScrollY, setLastScrollY] = useState(0)

  const searchSectionRef = useRef<HTMLDivElement | null>(null)
  const resultsContainerRef = useRef<HTMLDivElement | null>(null)
  const isLoadingInitialData = useRef(false)

  const resolveDateTime = (rawDateTime?: string | null, date?: string, time?: string) => {
    if (rawDateTime) {
      const parsed = new Date(rawDateTime)
      if (!Number.isNaN(parsed.getTime())) {
        return parsed.toISOString()
      }
    }

    if (date && time) {
      const trimmed = time.trim()
      const hasMeridiem = /\b(AM|PM)\b/i.test(trimmed)

      const normalized = (() => {
        if (hasMeridiem) {
          return trimmed
        }

        if (/^\d{1,2}:\d{2}(?::\d{2})?$/.test(trimmed)) {
          return trimmed.length === 5 ? `${trimmed}:00` : trimmed
        }

        return trimmed
      })()

      const composed = `${date} ${normalized}`
      const parsed = new Date(composed)
      if (!Number.isNaN(parsed.getTime())) {
        return parsed.toISOString()
      }
    }

    if (time) {
      const parsed = new Date(`1970-01-01 ${time.trim()}`)
      if (!Number.isNaN(parsed.getTime())) {
        return parsed.toISOString()
      }
    }

    return undefined
  }

  const getDisplayTime = useCallback((iso?: string, fallback?: string) => {
    if (iso) {
      const parsed = new Date(iso)
      if (!Number.isNaN(parsed.getTime())) {
        // Use timezone-aware formatter instead of direct format
        return formatTimeOnly(parsed.toISOString())
      }
    }
    return fallback || '--:--'
  }, [formatTimeOnly])

  const mapFlightToUi = useCallback((flight: ApiFlight, searchDate?: string): FlightSearchResult => {
    const departureDateTime = resolveDateTime(flight.departureDateTime, searchDate, flight.departureTime)
    const arrivalDateTime = resolveDateTime(flight.arrivalDateTime, searchDate, flight.arrivalTime)

    return {
      id: flight.flightId,
      airline: flight.airline,
      flightNumber: flight.flightNumber,
      origin: flight.origin,
      destination: flight.destination,
      departureTime: departureDateTime || flight.departureTime,
      arrivalTime: arrivalDateTime || flight.arrivalTime,
      departureDateTime,
      arrivalDateTime,
      currency: flight.currency || 'VND',
      seatClass: flight.seatClass || 'ECONOMY',
      logo: flight.airlineLogo || '/airplane-generic.png', // Use airlineLogo from backend
      scheduleId: flight.scheduleId,
      fareId: flight.fareId,
      departure: {
        time: getDisplayTime(departureDateTime, flight.departureTime),
        airport: flight.origin,
        city: flight.origin,
      },
      arrival: {
        time: getDisplayTime(arrivalDateTime, flight.arrivalTime),
        airport: flight.destination,
        city: flight.destination,
      },
      duration: flight.duration || '',
      stops: flight.stops && flight.stops > 0 ? `${flight.stops} ${flight.stops === 1 ? 'điểm dừng' : 'điểm dừng'}` : 'Bay thẳng',
      price: flight.price,
      class: flight.seatClass || 'ECONOMY',
      rating: 4.5,
      raw: {
        ...flight,
        departureDateTime,
        arrivalDateTime,
      },
    }
  }, [getDisplayTime])

  const scrollToSearch = () => {
    searchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const handleViewDetails = (flight: FlightSearchResult) => {
    setSelectedFlightForModal(flight)
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setSelectedFlightForModal(null)
  }

  const handleOriginSelect = (city: City) => {
    setOrigin(city)
  }

  const handleDestinationSelect = (city: City) => {
    setDestination(city)
  }

  const startFlightBooking = (flight: FlightSearchResult, options: { allowWithoutSearch?: boolean } = {}) => {
    if (!hasSearched && !options.allowWithoutSearch) {
      scrollToSearch()
      return
    }

    const flightData = flight?.raw || flight
    const flightId = flightData?.flightId || flight?.id

    if (!flightData || !flightId) {
      console.error('Unable to start booking flow: missing flight details')
      return
    }

    const departureDateTime = resolveDateTime(
      flightData.departureDateTime,
      departDate,
      typeof flightData.departureTime === 'string' ? flightData.departureTime : undefined,
    )
    const arrivalDateTime = resolveDateTime(
      flightData.arrivalDateTime,
      departDate,
      typeof flightData.arrivalTime === 'string' ? flightData.arrivalTime : undefined,
    )
    const airlineLogo = flight?.logo || flightData.airlineLogo
    const normalizedSeatClass = (flightData.seatClass || flight?.seatClass || flight?.class || 'ECONOMY').toString().toUpperCase()
    const ticketPrice = Number(flightData.price ?? flight?.price ?? 0)
    const flightPayload = {
      flightId: flightId,
      flightNumber: flightData.flightNumber,
      airline: flightData.airline,
      origin: flightData.origin,
      destination: flightData.destination,
      originLatitude: flightData.originLatitude,
      originLongitude: flightData.originLongitude,
      destinationLatitude: flightData.destinationLatitude,
      destinationLongitude: flightData.destinationLongitude,
      departureTime: departureDateTime || flightData.departureTime,
      arrivalTime: arrivalDateTime || flightData.arrivalTime,
      duration: flightData.duration,
      price: ticketPrice,
      currency: flightData.currency || 'VND',
      seatClass: normalizedSeatClass,
      logo: airlineLogo,
      scheduleId: flightData.scheduleId,
      fareId: flightData.fareId,
    }
    const hasHotelSelection = Boolean(selectedHotel)

    if (hasHotelSelection) {
      setBookingType('both')
      setFlightDetails(null)
      updateBookingData({
        bookingType: 'COMBO',
        totalAmount: 0,
        currency: flightData.currency || 'VND',
        flightSelection: undefined,
      })
    } else {
      resetBooking()
      setBookingType('flight')
      setFlightDetails(null)
      updateBookingData({
        bookingType: 'FLIGHT',
        totalAmount: 0,
        currency: flightData.currency || 'VND',
        flightSelection: undefined,
        hotelSelection: undefined,
        comboDiscount: undefined,
      })
    }

    setSelectedFlight(flightPayload)
    setStep('passengers')
    
    // Ensure all context updates are flushed before proceeding
    setTimeout(() => {
      // Use callback if provided (for modal), otherwise navigate to booking page
      if (onBookingStart) {
        onBookingStart()
      } else {
        router.push('/bookings')
      }
    }, 0)
  }

  const handleModalBookFlight = (details: FlightDetails) => {
    const departureIso = resolveDateTime(details.departureDateTime, departDate, details.departureTime)
    const arrivalIso = resolveDateTime(details.arrivalDateTime, departDate, details.arrivalTime)

    const normalized = {
      raw: {
        flightId: details.flightId,
        flightNumber: details.flightNumber,
        airline: details.airline,
        origin: details.origin,
        destination: details.destination,
        originLatitude: details.originLatitude,
        originLongitude: details.originLongitude,
        destinationLatitude: details.destinationLatitude,
        destinationLongitude: details.destinationLongitude,
        departureDateTime: departureIso || details.departureTime,
        arrivalDateTime: arrivalIso || details.arrivalTime,
        duration: details.duration,
        price: details.price,
        currency: details.currency,
        seatClass: details.seatClass,
        airlineLogo: '/airplane-generic.png',
        departureTime: details.departureTime,
        arrivalTime: details.arrivalTime,
        scheduleId: details.scheduleId,
        fareId: details.fareId,
      },
      id: details.flightId,
      airline: details.airline,
      flightNumber: details.flightNumber,
      origin: details.origin,
      destination: details.destination,
      departureTime: departureIso || details.departureTime,
      arrivalTime: arrivalIso || details.arrivalTime,
      duration: details.duration,
      price: details.price,
      currency: details.currency,
      seatClass: details.seatClass,
      logo: '/airplane-generic.png',
      scheduleId: details.scheduleId,
      fareId: details.fareId,
      departureDateTime: departureIso || details.departureTime,
      arrivalDateTime: arrivalIso || details.arrivalTime,
    }

    startFlightBooking(normalized, { allowWithoutSearch: true })
    handleCloseModal()
  }

  const loadInitialData = useCallback(async () => {
    if (isLoadingInitialData.current) {
      return
    }

    isLoadingInitialData.current = true
    setLoading(true)
    setError(null)
    try {
      const res = await flightService.search({
        origin: "",
        destination: "",
        departureDate: "",
        returnDate: undefined,
        passengers: 1,
        seatClass: "ECONOMY",
        sortBy: "departure",
        page: 1,
        limit,
      })
      setInitialData(res as InitialFlightData)
      
      const ui = (res.flights || []).map((f: ApiFlight) => mapFlightToUi(f))
      setFlightResults(ui)
      setHasMore(Boolean(res.hasMore))
    } catch (e: any) {
      setError(e?.message || "Failed to load initial flight data")
    } finally {
      setLoading(false)
      isLoadingInitialData.current = false
    }
  }, [limit, mapFlightToUi])

  async function handleSearch(nextPage?: number) {
    if (!origin || !destination || !departDate) {
      setError('Vui lòng chọn điểm đi, điểm đến và ngày khởi hành để xem giá')
      scrollToSearch()
      return
    }

    if (origin.code === destination.code) {
      setError('Điểm đi và điểm đến không được trùng nhau')
      scrollToSearch()
      return
    }

    const passengerCount = parseInt(passengers || '0', 10)
    if (!Number.isFinite(passengerCount) || passengerCount <= 0) {
      setError('Vui lòng chọn số lượng hành khách hợp lệ')
      scrollToSearch()
      return
    }

    setLoading(true)
    setError(null)
    try {
      const usePage = nextPage ?? page
      const res = await flightService.search({
        origin: origin?.name || "",
        destination: destination?.name || "",
        departureDate: departDate,
        returnDate: returnDate || undefined,
        passengers: parseInt(passengers || "1", 10) || 1,
        seatClass,
        sortBy,
        page: usePage,
        limit,
      })
      const ui = (res.flights || []).map((f: ApiFlight) => mapFlightToUi(f, departDate))
      setFlightResults(ui)
      setHasMore(Boolean(res.hasMore))
      setPage(usePage)
      setHasSearched(true)
    } catch (e: any) {
      setError(e?.message || "Failed to load flights")
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
    if (flightResults.length === 0 && !loading && !initialData) {
      void loadInitialData()
    }
  }, [flightResults.length, loading, initialData, loadInitialData])

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
                  <label className="text-sm font-medium">Đi từ</label>
                  <Button
                    variant="outline"
                    className="w-full justify-start text-left font-normal h-8 border-2 text-sm"
                    onClick={() => setIsOriginModalOpen(true)}
                  >
                    {origin ? (
                      <span className="truncate">{origin.name}</span>
                    ) : (
                      <span className="text-muted-foreground">Chọn thành phố khởi hành...</span>
                    )}
                  </Button>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Đến</label>
                  <Button
                    variant="outline"
                    className="w-full justify-start text-left font-normal h-8 border-2 text-sm"
                    onClick={() => setIsDestinationModalOpen(true)}
                  >
                    {destination ? (
                      <span className="truncate">{destination.name}</span>
                    ) : (
                      <span className="text-muted-foreground">Chọn thành phố đến...</span>
                    )}
                  </Button>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Ngày đi</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-3 w-3 text-gray-400" />
                    <Input 
                      type="date" 
                      className="pl-8 h-8 border-2 text-sm" 
                      value={departDate} 
                      onChange={(e) => setDepartDate(e.target.value)} 
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Ngày về (tùy chọn)</label>
                  <div className="relative">
                    <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-3 w-3 text-gray-400" />
                    <Input 
                      type="date" 
                      className="pl-8 h-8 border-2 text-sm" 
                      value={returnDate} 
                      onChange={(e) => setReturnDate(e.target.value)} 
                    />
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Hành khách</label>
                  <Select value={passengers} onValueChange={setPassengers}>
                    <SelectTrigger className="h-8 border-2 text-sm">
                      <Users className="h-3 w-3 mr-2" />
                      <SelectValue placeholder="1 Người lớn" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="1">1 Người lớn</SelectItem>
                      <SelectItem value="2">2 Người lớn</SelectItem>
                      <SelectItem value="3">3 Người lớn</SelectItem>
                      <SelectItem value="4">4 Người lớn</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Hạng ghế</label>
                  <Select value={seatClass} onValueChange={(v) => setSeatClass(v as FareClass)}>
                    <SelectTrigger className="h-8 border-2 text-sm">
                      <SelectValue placeholder="PHỔ THÔNG" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ECONOMY">Phổ thông</SelectItem>
                      <SelectItem value="PREMIUM_ECONOMY">Phổ thông đặc biệt</SelectItem>
                      <SelectItem value="BUSINESS">Thương gia</SelectItem>
                      <SelectItem value="FIRST">Hạng nhất</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex items-end">
                  <Button 
                    className="w-full h-8 bg-blue-600 text-white hover:bg-blue-700 text-sm font-medium" 
                    onClick={() => handleSearch()} 
                    disabled={loading}
                  >
                    <Search className="h-3 w-3 mr-2" />
                    {loading ? "Đang tìm kiếm..." : "Tìm chuyến bay"}
                  </Button>
                </div>
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
                    <label className="text-sm font-medium mb-3 block">Khoảng giá (VND)</label>
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

                  {/* Duration */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Thời gian bay (giờ)</label>
                    <Slider 
                      value={durationRange} 
                      onValueChange={setDurationRange} 
                      max={24} 
                      step={1} 
                      className="mb-2" 
                    />
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <span>{durationRange[0]}h</span>
                      <span>{durationRange[1]}h</span>
                    </div>
                  </div>

                  {/* Airlines */}
                  <div>
                    <label className="text-sm font-medium mb-3 block">Hãng hàng không</label>
                    <Select onValueChange={(value) => {
                      if (!selectedAirlines.includes(value)) {
                        setSelectedAirlines([...selectedAirlines, value])
                      }
                    }}>
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn hãng hàng không..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="vietnam-airlines">Vietnam Airlines</SelectItem>
                        <SelectItem value="vietjet">VietJet Air</SelectItem>
                        <SelectItem value="bamboo">Bamboo Airways</SelectItem>
                        <SelectItem value="jetstar">Jetstar Pacific</SelectItem>
                      </SelectContent>
                    </Select>
                    {selectedAirlines.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        {selectedAirlines.map((airline) => (
                          <div 
                            key={airline} 
                            className="bg-secondary text-secondary-foreground px-2 py-1 rounded-md text-sm flex items-center"
                          >
                            {airline}
                            <button 
                              className="ml-1"
                              onClick={() => setSelectedAirlines(selectedAirlines.filter(a => a !== airline))}
                            >
                              ×
                            </button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
              </div>
            </div>

            {/* Results */}
            <div className="lg:col-span-3">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-semibold">Kết quả tìm kiếm chuyến bay</h2>
                <div className="flex items-center gap-4">
                  <span className="text-sm text-muted-foreground">
                    {initialData ? `${initialData.totalCount || flightResults.length} chuyến bay` : `${flightResults.length} chuyến bay`}
                  </span>
                  <Select value={sortBy} onValueChange={(value) => {
                    setSortBy(value)
                    setTimeout(() => void handleSearch(), 0)
                  }}>
                    <SelectTrigger className="w-40">
                      <SelectValue placeholder="Sắp xếp theo giờ đi" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="price-low">Giá: Thấp đến cao</SelectItem>
                      <SelectItem value="price-high">Giá: Cao đến thấp</SelectItem>
                      <SelectItem value="duration">Thời gian bay</SelectItem>
                      <SelectItem value="departure">Giờ khởi hành</SelectItem>
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
                      <FlightCardSkeleton key={i} />
                    ))}
                  </div>
                )}

                {/* Flight Results */}
                {flightResults.map((flight) => {
                  const flightData = {
                    id: flight.id,
                    airline: flight.airline,
                    flightNumber: flight.flightNumber,
                    origin: flight.origin || flight.departure.city,
                    destination: flight.destination || flight.arrival.city,
                    departureTime: flight.departureTime,
                    arrivalTime: flight.arrivalTime,
                    departureDateTime: flight.departureDateTime,
                    arrivalDateTime: flight.arrivalDateTime,
                    duration: flight.duration,
                    stops: flight.stops,
                    price: flight.price,
                    currency: flight.currency,
                    seatClass: flight.seatClass,
                    class: flight.class,
                    logo: flight.logo,
                    scheduleId: flight.raw?.scheduleId,
                    fareId: flight.raw?.fareId,
                    rating: flight.rating,
                    raw: flight.raw,
                  }

                  return (
                    <FlightCard
                      key={flight.id}
                      flight={flightData}
                      onViewDetails={handleViewDetails}
                      onBook={startFlightBooking}
                      showBookButton={hasSearched}
                    />
                  )
                })}

                {/* Empty State */}
                {!loading && flightResults.length === 0 && !error && (
                  <div className="text-center py-20">
                    <div className="text-6xl mb-4">✈️</div>
                    <h2 className="text-2xl font-semibold mb-2">
                      {hasSearched ? "Không tìm thấy chuyến bay" : "Bắt đầu tìm chuyến bay của bạn"}
                    </h2>
                    <p className="text-muted-foreground">
                      {hasSearched
                        ? "Hãy thử điều chỉnh lại tiêu chí hoặc chọn ngày khởi hành khác."
                        : "Vui lòng nhập thông tin tìm kiếm để xem các chuyến bay phù hợp."
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
      <FlightDetailsModal
        flightId={selectedFlightForModal?.id}
        seatClass={selectedFlightForModal?.class}
        departureDateTime={selectedFlightForModal?.raw?.departureDateTime}
        scheduleId={selectedFlightForModal?.raw?.scheduleId}
        fareId={selectedFlightForModal?.raw?.fareId}
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        onBookFlight={handleModalBookFlight}
        canBook={hasSearched}
        onPromptSearch={scrollToSearch}
      />

      <FlightDestinationModal
        isOpen={isOriginModalOpen}
        onClose={() => setIsOriginModalOpen(false)}
        onSelect={handleOriginSelect}
        title="Chọn điểm khởi hành"
        placeholder="Tìm kiếm thành phố khởi hành..."
      />

      <FlightDestinationModal
        isOpen={isDestinationModalOpen}
        onClose={() => setIsDestinationModalOpen(false)}
        onSelect={handleDestinationSelect}
        title="Chọn điểm đến"
        placeholder="Tìm kiếm thành phố đến..."
      />
    </div>
  )
}

import React, { useMemo, useCallback, memo, useState } from 'react'
import { Sparkles, Info, MapPin, AlertTriangle, CheckCircle, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import Image from 'next/image'
import type { 
  ChatStructuredResult, 
  ConfirmationContext,
} from '@/modules/ai/types'
import {FlightCard, HotelCard} from "@/components/cards";
import { ResultsSkeleton } from "@/components/StreamingIndicator";
import FlightDetailsModal from "@/modules/flight/component/FlightDetailsModal";

// Types that were likely intended to be here or in a local types file.
interface FlightDataForCard {
  flightId: number;
  airline: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  departureDateTime?: string;
  arrivalDateTime?: string;
  duration: string;
  stops?: string | number;
  price?: number;
  currency: string;
  seatClass: string;
  logo?: string;
  scheduleId?: string;
  fareId?: string;
  rating?: number;
  originLatitude?: number;
  originLongitude?: number;
  destinationLatitude?: number;
  destinationLongitude?: number;
  raw: any;
}

interface HotelDataForCard {
  id: string;
  name: string;
  image?: string;
  location?: string;
  city?: string;
  country?: string;
  rating?: number;
  reviews?: number;
  price?: number;
  originalPrice?: number;
  currency: string;
  amenities?: string[];
  description?: string;
  starRating?: number;
  latitude?: number;
  longitude?: number;
}

interface LocationClickData {
  lat: number;
  lng: number;
  title: string;
  description?: string;
}


interface AiResponseRendererProps {
  message: string
  results?: ChatStructuredResult[]
  nextRequestSuggestions?: string[]
  requiresConfirmation?: boolean
  confirmationContext?: ConfirmationContext
  onFlightBook?: (flight: FlightDataForCard) => void
  onHotelBook?: (hotel: HotelDataForCard) => void
  onLocationClick?: (location: LocationClickData) => void
  onConfirm?: (context: ConfirmationContext) => void
  onCancel?: () => void
  canBook?: boolean
  isLoading?: boolean
}

// Memoized sub-components for better performance
const FlightResultsSection = memo(({
  results,
  onViewDetails,
  onBook,
  onLocationClick,
  canBook
}: {
  results: ChatStructuredResult[]
  onViewDetails: (flight: FlightDataForCard) => void
  onBook: (flight: FlightDataForCard) => void
  onLocationClick?: (location: LocationClickData) => void
  canBook: boolean
}) => {
  if (results.length === 0) return null

  return (
    <div className="space-y-3 animate-fadeIn">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
        <Sparkles className="h-4 w-4 text-blue-500" />
        <span>Chuyến bay gợi ý ({results.length})</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {results.map((result, index) => {
          const metadata = result.metadata ?? {}
          const flightId = Number(result.ids?.flightId) || Number(metadata?.flightId || metadata?.id || `flight-${index}`)
          const scheduleId = result.ids?.scheduleId || (metadata?.scheduleId ? String(metadata?.scheduleId) : undefined)
          const fareId = result.ids?.fareId || (metadata?.fareId ? String(metadata?.fareId) : undefined)

          const subtitleInfo = parseFlightSubtitle(result.subtitle || optionalString(metadata?.subtitle))
          const { airlineName, flightNumber: titleFlightNumber } = extractFlightInfoFromTitle(
              optionalString(result.title || metadata?.title)
          )

          const priceSource = metadata?.price ?? metadata?.totalPrice ?? metadata?.fare ?? metadata?.amount ?? metadata?.pricePerPerson
          const price = parsePriceToNumber(priceSource)

          const currencyCandidate =
              optionalString(metadata?.currency) ||
              optionalString(metadata?.currencyCode) ||
              optionalString(metadata?.priceCurrency)

          const normalizedCurrencyCandidate = currencyCandidate?.replace(/[^A-Za-z]/g, "").toUpperCase()

          const currency =
              normalizedCurrencyCandidate ||
              extractCurrency(currencyCandidate ?? priceSource ?? metadata?.currency ?? metadata?.price, "VND")

          const originRaw = metadata?.origin ?? metadata?.departureAirport ?? metadata?.departure_airport ?? metadata?.from ?? subtitleInfo.origin
          const destinationRaw = metadata?.destination ?? metadata?.arrivalAirport ?? metadata?.arrival_airport ?? metadata?.to ?? subtitleInfo.destination

          const departureTimeRaw =
              metadata?.departureTime ??
              metadata?.departure_time ??
              metadata?.departureDateTime ??
              metadata?.departure_time_local ??
              subtitleInfo.departureTime

          const arrivalTimeRaw =
              metadata?.arrivalTime ??
              metadata?.arrival_time ??
              metadata?.arrivalDateTime ??
              metadata?.arrival_time_local ??
              subtitleInfo.arrivalTime

          const seatClassRaw = metadata?.seatClass ?? metadata?.seat_class ?? metadata?.cabinClass ?? metadata?.class

          const logo =
              optionalString(result.imageUrl) ||
              optionalString(metadata?.airlineLogo) ||
              optionalString(metadata?.airline_logo) ||
              optionalString(metadata?.logo) ||
              optionalString(metadata?.logoUrl)

          const stopsRaw = metadata?.stops ?? metadata?.stopCount ?? metadata?.number_of_stops ?? metadata?.stopsCount
          const stopsValue =
              typeof stopsRaw === "number"
                  ? stopsRaw
                  : typeof stopsRaw === "string" && stopsRaw.trim().length > 0
                      ? stopsRaw
                      : undefined

          const flightData: FlightDataForCard = {
              flightId: flightId,
              airline: optionalString(metadata?.airline) || airlineName || optionalString(result.title) || "",
              flightNumber: optionalString(metadata?.flightNumber) || optionalString(metadata?.flight_number) || optionalString(metadata?.code) || titleFlightNumber || "",
              origin: optionalString(originRaw) || "",
              destination: optionalString(destinationRaw) || "",
              departureTime: optionalString(departureTimeRaw) || "",
              arrivalTime: optionalString(arrivalTimeRaw) || "",
              departureDateTime: optionalString(metadata?.departureDateTime) || optionalString(metadata?.departure_date_time) || optionalString(metadata?.departureDate) || optionalString(metadata?.departure_date) || optionalString(metadata?.departureTime),
              arrivalDateTime: optionalString(metadata?.arrivalDateTime) || optionalString(metadata?.arrival_date_time) || optionalString(metadata?.arrivalDate) || optionalString(metadata?.arrival_date) || optionalString(metadata?.arrivalTime),
              duration: optionalString(metadata?.duration) || optionalString(metadata?.flightDuration) || optionalString(metadata?.travelTime) || "",
              stops: stopsValue,
              price,
              currency,
              seatClass: optionalString(seatClassRaw) || "ECONOMY",
              logo,
              scheduleId,
              fareId,
              rating: optionalNumber(metadata?.rating),
              originLatitude: optionalNumber(metadata?.originLatitude),
              originLongitude: optionalNumber(metadata?.originLongitude),
              destinationLatitude: optionalNumber(metadata?.destinationLatitude),
              destinationLongitude: optionalNumber(metadata?.destinationLongitude),
              raw: {
                  flightId,
                  flightNumber: optionalString(metadata?.flightNumber) || optionalString(metadata?.flight_number) || optionalString(metadata?.code) || titleFlightNumber,
                  airline: optionalString(metadata?.airline) || airlineName || optionalString(result.title),
                  origin: optionalString(originRaw),
                  destination: optionalString(destinationRaw),
                  departureDateTime: optionalString(metadata?.departureDateTime) || optionalString(metadata?.departure_date_time) || optionalString(metadata?.departureDate) || optionalString(metadata?.departure_date) || optionalString(metadata?.departureTime),
                  arrivalDateTime: optionalString(metadata?.arrivalDateTime) || optionalString(metadata?.arrival_date_time) || optionalString(metadata?.arrivalDate) || optionalString(metadata?.arrival_date) || optionalString(metadata?.arrivalTime),
                  departureTime: optionalString(departureTimeRaw),
                  arrivalTime: optionalString(arrivalTimeRaw),
                  scheduleId,
                  fareId,
                  seatClass: optionalString(seatClassRaw) || "ECONOMY",
                  price,
                  currency,
              },
          }

          return (
              <FlightCard
                  key={flightData.flightId}
                  flight={flightData}
                  onViewDetails={onViewDetails}
                  onBook={() => onBook(flightData)}
                  onLocationClick={onLocationClick}
                  showBookButton={canBook}
                  compact={false}
                  className="h-full"
              />
          )
        })}
      </div>
    </div>
  )
})
FlightResultsSection.displayName = 'FlightResultsSection'

const HotelResultsSection = memo(({
  results,
  onViewDetails,
  onBook,
  onLocationClick,
  canBook
}: {
  results: ChatStructuredResult[]
  onViewDetails: (hotel: HotelDataForCard) => void
  onBook: (hotel: HotelDataForCard) => void
  onLocationClick?: (location: LocationClickData) => void
  canBook: boolean
}) => {
  if (results.length === 0) return null

  return (
    <div className="space-y-3 animate-fadeIn">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
        <Sparkles className="h-4 w-4 text-blue-500" />
        <span>Khách sạn gợi ý ({results.length})</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {results.map((result, index) => {
          const metadata = result.metadata ?? {}
          const hotelId = result.ids?.hotelId || String(metadata?.hotelId || metadata?.id || `hotel-${index}`)

          const subtitleInfo = parseHotelSubtitle(result.subtitle || optionalString(metadata?.subtitle))

          const priceSource = metadata?.price ?? metadata?.pricePerNight ?? metadata?.price_per_night ?? metadata?.amount ?? metadata?.lowestPrice
          const price = parsePriceToNumber(priceSource)

          const currencyCandidate =
              optionalString(metadata?.currency) ||
              optionalString(metadata?.currencyCode) ||
              optionalString(metadata?.priceCurrency)

          const normalizedCurrencyCandidate = currencyCandidate?.replace(/[^A-Za-z]/g, "").toUpperCase()

          const currency =
              normalizedCurrencyCandidate ||
              extractCurrency(currencyCandidate ?? priceSource ?? metadata?.currency ?? metadata?.price, "VND")

          const ratingRaw = metadata?.rating ?? metadata?.reviewScore ?? metadata?.averageRating
          const reviewsRaw = metadata?.reviews ?? metadata?.reviewCount
          const city = optionalString(metadata?.city)
          const country = optionalString(metadata?.country)
          const location =
              optionalString(metadata?.location) ||
              subtitleInfo.location ||
              (city && country ? `${city}, ${country}` : city || country) ||
              undefined

          const hotelData: HotelDataForCard = {
              id: hotelId,
              name: optionalString(result.title) || optionalString(metadata?.name) || "",
              image:
                  optionalString(result.imageUrl) ||
                  optionalString(metadata?.primaryImage) ||
                  optionalString(metadata?.image) ||
                  optionalString(metadata?.thumbnail),
              location,
              city,
              country,
              rating: optionalNumber(ratingRaw) ?? subtitleInfo.rating,
              reviews: optionalNumber(reviewsRaw),
              price,
              originalPrice: optionalNumber(metadata?.originalPrice),
              currency,
              amenities: Array.isArray(metadata?.amenities) ? (metadata?.amenities as string[]) : undefined,
              description: optionalString(result.description) || optionalString(metadata?.description),
              starRating: optionalNumber(metadata?.starRating) ?? subtitleInfo.rating,
              latitude: optionalNumber(metadata?.latitude),
              longitude: optionalNumber(metadata?.longitude),
          }

          return (
              <HotelCard
                  key={hotelData.id}
                  hotel={hotelData}
                  onViewDetails={onViewDetails}
                  onBook={() => onBook(hotelData)}
                  onLocationClick={onLocationClick}
                  showBookButton={canBook}
                  compact={false}
                  className="h-full"
              />
          )
        })}
      </div>
    </div>
  )
})
HotelResultsSection.displayName = 'HotelResultsSection'

const InfoResultsSection = memo(({
  results,
  onLocationClick
}: {
  results: ChatStructuredResult[]
  onLocationClick?: (location: LocationClickData) => void
}) => {
  if (results.length === 0) return null

  return (
    <div className="space-y-3 animate-fadeIn">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
        <Info className="h-4 w-4 text-blue-500" />
        <span>Thông tin địa điểm ({results.length})</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {results.map((result, index) => {
          const metadata = (result.metadata || {}) as Record<string, unknown>
          const metadataAny = metadata as Record<string, any>
          const hasCoordinates = Boolean(metadataAny.coordinates || (metadataAny.latitude && metadataAny.longitude))
          
          const imageUrl = result.imageUrl ||
                           optionalString(metadataAny.imageUrl) ||
                           optionalString(metadataAny.image_url) ||
                           optionalString(metadataAny.image) ||
                           optionalString(metadataAny.thumbnail)
          
          const handleLocationClick = () => {
            if (!hasCoordinates || !onLocationClick) return
            
            let lat: number | undefined
            let lng: number | undefined
            
            if (metadataAny.coordinates) {
              const coordStr = String(metadataAny.coordinates)
              const [latStr, lngStr] = coordStr.split(',').map(s => s.trim())
              lat = parseFloat(latStr)
              lng = parseFloat(lngStr)
            }

            if (!lat || !lng) {
              lat = metadataAny.latitude ? parseFloat(String(metadataAny.latitude)) : undefined
              lng = metadataAny.longitude ? parseFloat(String(metadataAny.longitude)) : undefined
            }

            if (lat && lng && !isNaN(lat) && !isNaN(lng)) {
              const locationDesc = result.subtitle || (typeof metadataAny.location === 'string' ? metadataAny.location : undefined) || result.description
              onLocationClick({
                lat,
                lng,
                title: result.title || 'Location',
                description: locationDesc
              })
            }
          }
          
          const isClickable = hasCoordinates && Boolean(onLocationClick)
          return (
            <div 
              key={`info-${index}`}
              className={cn(
                "bg-white border border-gray-200 rounded-lg overflow-hidden shadow-sm",
                isClickable && "cursor-pointer hover:shadow-md hover:border-blue-300 transition-all duration-200"
              )}
              onClick={handleLocationClick}
            >
              {imageUrl && (
                <div className="relative h-48 w-full">
                  <Image
                    src={imageUrl}
                    alt={result.title || 'Location image'}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      const target = e.target as HTMLImageElement
                      target.style.display = 'none'
                    }}
                  />
                </div>
              )}
              
              <div className="p-4">
                {result.title && (
                  <h3 className="font-semibold text-gray-900 mb-2">{result.title}</h3>
                )}
                {result.subtitle && (
                  <p className="text-sm text-gray-600 mb-3">{result.subtitle}</p>
                )}
                {result.description && (
                  <p className="text-sm text-gray-700 mb-3">{result.description}</p>
                )}
                
                {optionalString(metadata.best_time) && (
                  <div className="text-xs text-green-600 mb-2">
                    <span className="font-medium">Thời gian lý tưởng:</span> {optionalString(metadata.best_time)}
                  </div>
                )}
                {optionalString(metadata.estimated_cost) && (
                  <div className="text-xs text-orange-600 mb-2">
                    <span className="font-medium">Chi phí ước tính:</span> {optionalString(metadata.estimated_cost)}
                  </div>
                )}
                {Array.isArray(metadata.highlights) && metadata.highlights.length > 0 && (
                  <div className="text-xs text-blue-600 mb-3">
                    <span className="font-medium">Điểm nổi bật:</span>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {(metadata.highlights as string[]).slice(0, 3).map((highlight, idx) => (
                        <span key={idx} className="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs">
                          {highlight}
                        </span>
                      ))}
                      {(metadata.highlights as string[]).length > 3 && (
                        <span className="text-blue-600 text-xs">+{(metadata.highlights as string[]).length - 3} more</span>
                      )}
                    </div>
                  </div>
                )}
                
                {isClickable && (
                  <div className="text-xs text-blue-600 font-medium flex items-center gap-1">
                    <MapPin className="h-3 w-3" />
                    Click để xem trên bản đồ
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
})
InfoResultsSection.displayName = 'InfoResultsSection'

const ConfirmationSection = memo(({
  context,
  onConfirm,
  onCancel
}: {
  context: ConfirmationContext
  onConfirm?: (context: ConfirmationContext) => void
  onCancel?: () => void
}) => {
  return (
    <div className="mt-6 p-6 bg-gradient-to-r from-yellow-50 to-orange-50 border-2 border-yellow-400 rounded-xl shadow-lg animate-fadeIn">
      <div className="flex items-center gap-3 mb-4">
        <div className="p-2 bg-yellow-400 rounded-lg">
          <AlertTriangle className="h-6 w-6 text-yellow-900" />
        </div>
        <div>
          <h3 className="text-lg font-bold text-gray-900">
            ⚠️ Xác nhận yêu cầu 
          </h3>
          <p className="text-sm text-gray-600">
            Vui lòng xác nhận trước khi tiếp tục 
          </p>
        </div>
      </div>

      <div className="mb-4 p-4 bg-white rounded-lg border border-yellow-200">
        <p className="text-sm font-medium text-gray-700 mb-2">
          Thao tác: <span className="text-blue-600">{context.operation}</span>
        </p>
        <div className="text-sm text-gray-800 whitespace-pre-line">
          {context.summary}
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <Button
          onClick={() => onConfirm?.(context)}
          className="flex-1 bg-green-600 hover:bg-green-700 text-white font-semibold py-3 px-6 rounded-lg shadow-md hover:shadow-lg transition-all duration-200 flex items-center justify-center gap-2"
        >
          <CheckCircle className="h-5 w-5" />
          <span>✅ Yes, Proceed / Xác nhận</span>
        </Button>
        
        <Button
          onClick={onCancel}
          variant="outline"
          className="flex-1 bg-white hover:bg-red-50 text-red-600 border-2 border-red-300 hover:border-red-400 font-semibold py-3 px-6 rounded-lg shadow-md hover:shadow-lg transition-all duration-200 flex items-center justify-center gap-2"
        >
          <XCircle className="h-5 w-5" />
          <span>❌ Cancel / Hủy bỏ</span>
        </Button>
      </div>

      <p className="mt-4 text-xs text-gray-500 text-center">
        🔒 Hệ thống yêu cầu xác nhận để bảo vệ tài khoản của bạn / 
        System requires confirmation to protect your account
      </p>
    </div>
  )
})
ConfirmationSection.displayName = 'ConfirmationSection'

export function AiResponseRenderer({
  message,
  results = [],
  requiresConfirmation = false,
  confirmationContext,
  onFlightBook,
  onHotelBook,
  onLocationClick,
  onConfirm,
  onCancel,
  canBook = true,
  isLoading = false,
}: AiResponseRendererProps) {
  // State for flight details modal
  const [selectedFlightForModal, setSelectedFlightForModal] = useState<FlightDataForCard | null>(null);
  const [isFlightModalOpen, setIsFlightModalOpen] = useState(false);

  // Normalize results to always be an array
  const normalizedResults = useMemo(() => 
    Array.isArray(results) ? results : [], 
    [results]
  )

  // Filter results by type - only recalculate when results change
  const flightResults = useMemo(
    () => normalizedResults.filter((r) => r.type === "flight"),
    [normalizedResults]
  )
  
  const hotelResults = useMemo(
    () => normalizedResults.filter((r) => r.type === "hotel"),
    [normalizedResults]
  )
  
  const infoResults = useMemo(
    () => normalizedResults.filter((r) => 
      r.type === "info" || r.type === "location" || r.type === "weather"
    ),
    [normalizedResults]
  )

  // Handle flight view details - opens flight details modal
  const handleFlightViewDetails = useCallback((flight: FlightDataForCard) => {
    setSelectedFlightForModal(flight);
    setIsFlightModalOpen(true);
  }, [])

  // Handle flight booking - called from the flight details modal
  const handleFlightBookFromModal = useCallback((flight: FlightDataForCard) => {
    onFlightBook?.(flight);
  }, [onFlightBook])

  const handleHotelViewDetails = useCallback((hotel: HotelDataForCard) => {
    onHotelBook?.(hotel);
  }, [onHotelBook])

  const hasAnyResults = flightResults.length > 0 || hotelResults.length > 0 || infoResults.length > 0;
  const showSkeleton = isLoading && (!message || message.trim().length === 0);


  return (
    <div className="space-y-4">
      {/* AI Message Text */}
      <div className="prose prose-sm max-w-none">
        {showSkeleton ? (
          <div className="space-y-3" aria-live="polite">
            <span className="sr-only">AI đang xử lý yêu cầu của bạn</span>
            <ResultsSkeleton count={2} />
          </div>
        ) : (
          message.trim() && <div className="whitespace-pre-wrap text-gray-800 leading-relaxed">{message}</div>
        )}
      </div>

      {/* Results */}
      {hasAnyResults && (
        <>
          {/* Flight Results */}
          {flightResults.length > 0 && (
              <FlightResultsSection
                  results={flightResults}
                  onViewDetails={handleFlightViewDetails}
                  onBook={handleFlightViewDetails}
                  onLocationClick={onLocationClick}
                  canBook={canBook}
              />
          )}

          {/* Hotel Results */}
          {hotelResults.length > 0 && (
              <HotelResultsSection
                  results={hotelResults}
                  onViewDetails={handleHotelViewDetails}
                  onBook={handleHotelViewDetails}
                  onLocationClick={onLocationClick}
                  canBook={canBook}
              />
          )}

          {/* Info Results */}
          {infoResults.length > 0 && (
              <InfoResultsSection
                  results={infoResults}
                  onLocationClick={onLocationClick}
              />
          )}
        </>
      )}

      {/* Confirmation UI */}
      {requiresConfirmation && confirmationContext && (
        <ConfirmationSection
          context={confirmationContext}
          onConfirm={onConfirm}
          onCancel={onCancel}
        />
      )}

      {/* Flight Details Modal */}
      <FlightDetailsModalComponent
        selectedFlight={selectedFlightForModal}
        isOpen={isFlightModalOpen}
        onClose={() => {
          setIsFlightModalOpen(false);
          setSelectedFlightForModal(null);
        }}
        onBookFlight={handleFlightBookFromModal}
        canBook={canBook}
      />
    </div>
  )
}

// Helper functions
function optionalString(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim()
  }
  return undefined
}

function optionalNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && !isNaN(value)) {
    return value
  }
  if (typeof value === 'string') {
    const num = parseFloat(value)
    if (!isNaN(num)) return num
  }
  return undefined
}

function parsePriceToNumber(value: unknown): number | undefined {
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    // Handle Vietnamese price formats like "1.334.933 VND" or "Gia la 1.334.933 VND"
    // Extract the numeric part by removing currency symbols and text
    const numericPart = value
      .replace(/[^0-9.,\s]/g, ' ') // Keep only digits, periods, commas, and spaces
      .replace(/\s+/g, ' ') // Normalize spaces
      .trim()
      .split(/\s+/) // Split by spaces
      .find(part => /[0-9]/.test(part)) // Find the part with digits
    
    if (numericPart) {
      // For Vietnamese format: 1.334.933 (period as thousand separator)
      const cleaned = numericPart
        .replace(/\./g, '') // Remove thousand separators (periods)
        .replace(/,/, '.') // Replace comma with period for decimal
      
      const num = parseFloat(cleaned)
      return isNaN(num) ? undefined : num
    }
  }
  return undefined
}

function extractCurrency(value: unknown, defaultCurrency: string = 'VND'): string {
  if (typeof value === 'string') {
    const match = value.match(/[A-Z]{3}/)
    if (match) return match[0]
  }
  return defaultCurrency
}

const parseFlightSubtitle = (subtitle?: string) => {
  if (!subtitle) return {}

  const [routePart, timePart] = subtitle.split("•").map((part) => part.trim())
  const result: {
    origin?: string
    destination?: string
    departureTime?: string
    arrivalTime?: string
  } = {}

  if (routePart) {
    const routeTokens = routePart
      .split(/→|->|—|–|-/)
      .map((token) => token.trim())
      .filter(Boolean)

    if (routeTokens.length >= 2) {
      result.origin = routeTokens[0]
      result.destination = routeTokens[routeTokens.length - 1]
    }
  }

  if (timePart) {
    const timeTokens = timePart
      .split(/-|–|—|→/)
      .map((token) => token.trim())
      .filter(Boolean)

    if (timeTokens.length >= 2) {
      result.departureTime = timeTokens[0]
      result.arrivalTime = timeTokens[timeTokens.length - 1]
    }
  }

  return result
}

const parseHotelSubtitle = (subtitle?: string) => {
  if (!subtitle) return {}

  const [locationPart, ratingPart] = subtitle.split("•").map((part) => part.trim())
  const ratingNumber = ratingPart ? parseFloat(ratingPart.replace(/[^0-9.]/g, "")) : undefined

  return {
    location: locationPart || undefined,
    rating: ratingNumber !== undefined && Number.isFinite(ratingNumber) ? ratingNumber : undefined,
  }
}

const extractFlightInfoFromTitle = (title?: string) => {
  if (!title) return {}

  const flightMatch = title.match(/\b[A-Z]{1,3}\d{1,4}\b/)
  const flightNumber = flightMatch ? flightMatch[0] : undefined

  const airlineName = flightNumber
    ? title
        .replace(flightNumber, "")
        .replace(/[•–—-]/g, " ")
        .split(/\s+/)
        .filter(Boolean)
        .join(" ")
    : title

  return {
    airlineName: airlineName?.trim() || undefined,
    flightNumber,
  }
}

// Flight Details Modal Component
const FlightDetailsModalComponent = ({
  selectedFlight,
  isOpen,
  onClose,
  onBookFlight,
  canBook = true,
}: {
  selectedFlight: FlightDataForCard | null;
  isOpen: boolean;
  onClose: () => void;
  onBookFlight: (flight: FlightDataForCard) => void;
  canBook?: boolean;
}) => {
  if (!selectedFlight || !isOpen) return null;

  return (
    <FlightDetailsModal
      flightId={selectedFlight.flightId ? Number(selectedFlight.flightId) : null}
      seatClass={selectedFlight.seatClass || null}
      departureDateTime={selectedFlight.departureDateTime || selectedFlight.departureTime || null}
      scheduleId={selectedFlight.scheduleId || null}
      fareId={selectedFlight.fareId || null}
      isOpen={isOpen}
      onClose={onClose}
      onBookFlight={onBookFlight}
      canBook={canBook}
    />
  );
};

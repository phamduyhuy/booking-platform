"use client"

import React, { createContext, useContext, useReducer, useEffect, useCallback, useRef } from 'react'
import { bookingService, type StorefrontBookingRequest, type StorefrontBookingResponse, type BookingStatusResponse, type StorefrontFlightSelection, type StorefrontHotelSelection } from '@/modules/booking/service'
import { useToast } from '@/hooks/use-toast'
import type { BookingHistoryItemDto, FlightBookingDetails, HotelBookingDetails, ComboBookingDetails } from '@/modules/booking/types'
import type { SelectedFlight, SelectedHotel } from '@/types'

// Types
type BookingStep = 'selection' | 'passengers' | 'review' | 'payment' | 'confirmation' | 'error'
type BookingType = 'flight' | 'hotel' | 'both'

interface ResumeBookingPayload {
  booking: BookingHistoryItemDto
  productDetails?: FlightBookingDetails | HotelBookingDetails | ComboBookingDetails | null
}

interface BookingState {
  step: BookingStep
  bookingType: BookingType | null
  bookingData: Partial<StorefrontBookingRequest>
  bookingResponse: StorefrontBookingResponse | null
  isLoading: boolean
  isStatusPolling: boolean
  error: string | null
  selectedFlight: SelectedFlight | null
  selectedHotel: SelectedHotel | null
  bookingStatus: BookingStatusResponse | null
  flightDetails: FlightBookingDetails | null
  hotelDetails: HotelBookingDetails | null
}

interface BookingContextType extends BookingState {
  setBookingType: (type: BookingType) => void
  updateBookingData: (data: Partial<StorefrontBookingRequest>) => void
  nextStep: () => void
  prevStep: () => void
  createBooking: () => Promise<void>
  resetBooking: () => void
  setError: (error: string | null) => void
  setStep: (step: BookingStep) => void
  setSelectedFlight: (flight: SelectedFlight | null) => void
  setSelectedHotel: (hotel: SelectedHotel | null) => void
  setFlightDetails: (details: FlightBookingDetails | null) => void
  setHotelDetails: (details: HotelBookingDetails | null) => void
  refreshBookingStatus: () => Promise<void>
  cancelInFlightBooking: () => Promise<void>
  resumeBooking: (payload: ResumeBookingPayload) => Promise<void>
}

// Initial state
const initialState: BookingState = {
  step: 'selection',
  bookingType: null,
  bookingData: {},
  bookingResponse: null,
  isLoading: false,
  isStatusPolling: false,
  error: null,
  selectedFlight: null,
  selectedHotel: null,
  bookingStatus: null,
  flightDetails: null,
  hotelDetails: null
}

const BOOKING_STORAGE_KEY = 'storefront-booking-state-v1'
const allowedSteps: BookingStep[] = ['selection', 'passengers', 'review', 'payment', 'confirmation', 'error']
const allowedBookingTypes: BookingType[] = ['flight', 'hotel', 'both']

type PersistedBookingState = Pick<
  BookingState,
  | 'step'
  | 'bookingType'
  | 'bookingData'
  | 'selectedFlight'
  | 'selectedHotel'
  | 'bookingResponse'
  | 'bookingStatus'
  | 'flightDetails'
  | 'hotelDetails'
>

function loadPersistedState(baseState: BookingState): BookingState {
  if (typeof window === 'undefined') {
    return baseState
  }

  try {
    const stored = window.sessionStorage.getItem(BOOKING_STORAGE_KEY)
    if (!stored) {
      return baseState
    }

    const parsed = JSON.parse(stored) as Partial<PersistedBookingState> | null
    if (!parsed || typeof parsed !== 'object') {
      return baseState
    }

    const step = parsed.step && allowedSteps.includes(parsed.step as BookingStep)
      ? (parsed.step as BookingStep)
      : baseState.step

    const bookingType = parsed.bookingType && allowedBookingTypes.includes(parsed.bookingType as BookingType)
      ? (parsed.bookingType as BookingType)
      : null

    return {
      ...baseState,
      step,
      bookingType,
      bookingData: {
        ...baseState.bookingData,
        ...(parsed.bookingData ?? {}),
      },
      selectedFlight: parsed.selectedFlight ?? null,
      selectedHotel: parsed.selectedHotel ?? null,
      bookingResponse: parsed.bookingResponse ?? null,
      bookingStatus: parsed.bookingStatus ?? null,
      flightDetails: parsed.flightDetails ?? null,
      hotelDetails: parsed.hotelDetails ?? null,
      isLoading: false,
      isStatusPolling: false,
      error: null,
    }
  } catch (error) {
    console.warn('Failed to load booking state from sessionStorage', error)
    return baseState
  }
}

// Actions
type BookingAction =
  | { type: 'SET_BOOKING_TYPE'; payload: BookingType }
  | { type: 'UPDATE_BOOKING_DATA'; payload: Partial<StorefrontBookingRequest> }
  | { type: 'SET_STEP'; payload: BookingStep }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_BOOKING_RESPONSE'; payload: StorefrontBookingResponse }
  | { type: 'SET_BOOKING_STATUS'; payload: BookingStatusResponse | null }
  | { type: 'SET_STATUS_POLLING'; payload: boolean }
  | { type: 'SET_SELECTED_FLIGHT'; payload: SelectedFlight | null }
  | { type: 'SET_SELECTED_HOTEL'; payload: SelectedHotel | null }
  | { type: 'SET_FLIGHT_DETAILS'; payload: FlightBookingDetails | null }
  | { type: 'SET_HOTEL_DETAILS'; payload: HotelBookingDetails | null }
  | { type: 'RESET_BOOKING' }

// Reducer
function bookingReducer(state: BookingState, action: BookingAction): BookingState {
  switch (action.type) {
    case 'SET_BOOKING_TYPE':
      return {
        ...state,
        bookingType: action.payload
      }
    case 'UPDATE_BOOKING_DATA':
      return {
        ...state,
        bookingData: {
          ...state.bookingData,
          ...action.payload
        }
      }
    case 'SET_STEP':
      return {
        ...state,
        step: action.payload
      }
    case 'SET_LOADING':
      return {
        ...state,
        isLoading: action.payload
      }
    case 'SET_ERROR':
      return {
        ...state,
        error: action.payload,
        step: action.payload ? 'error' : state.step
      }
    case 'SET_BOOKING_RESPONSE':
      return {
        ...state,
        bookingResponse: action.payload
      }
    case 'SET_BOOKING_STATUS':
      return {
        ...state,
        bookingStatus: action.payload
      }
    case 'SET_STATUS_POLLING':
      return {
        ...state,
        isStatusPolling: action.payload
      }
    case 'SET_SELECTED_FLIGHT':
      return {
        ...state,
        selectedFlight: action.payload
      }
    case 'SET_SELECTED_HOTEL':
      return {
        ...state,
        selectedHotel: action.payload
      }
    case 'SET_FLIGHT_DETAILS':
      return {
        ...state,
        flightDetails: action.payload
      }
    case 'SET_HOTEL_DETAILS':
      return {
        ...state,
        hotelDetails: action.payload
      }
    case 'RESET_BOOKING':
      return initialState
    default:
      return state
  }
}

// Context
const BookingContext = createContext<BookingContextType | undefined>(undefined)

// Provider
export function BookingProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(bookingReducer, initialState, loadPersistedState)
  const { toast } = useToast()
  const statusPollingRef = useRef<NodeJS.Timeout | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    if (typeof window === 'undefined') return

    try {
      const persistable: PersistedBookingState = {
        step: state.step,
        bookingType: state.bookingType,
        bookingData: state.bookingData,
        selectedFlight: state.selectedFlight,
        selectedHotel: state.selectedHotel,
        bookingResponse: state.bookingResponse,
        bookingStatus: state.bookingStatus,
        flightDetails: state.flightDetails,
        hotelDetails: state.hotelDetails,
      }
      window.sessionStorage.setItem(BOOKING_STORAGE_KEY, JSON.stringify(persistable))
    } catch (error) {
      console.warn('Failed to persist booking state to sessionStorage', error)
    }
  }, [
    state.step,
    state.bookingType,
    state.bookingData,
    state.selectedFlight,
    state.selectedHotel,
    state.bookingResponse,
    state.bookingStatus,
    state.flightDetails,
    state.hotelDetails,
  ])

  // Set mounted ref to false on unmount
  useEffect(() => {
    return () => {
      mountedRef.current = false
      if (statusPollingRef.current) {
        clearTimeout(statusPollingRef.current)
        statusPollingRef.current = null
      }
    }
  }, [])

  const setBookingType = useCallback((type: BookingType) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_BOOKING_TYPE', payload: type })
  }, [])

  const updateBookingData = useCallback((data: Partial<StorefrontBookingRequest>) => {
    if (!mountedRef.current) return
    dispatch({ type: 'UPDATE_BOOKING_DATA', payload: data })
  }, [])

  const setStep = useCallback((step: BookingStep) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_STEP', payload: step })
  }, [])

  const setSelectedFlight = useCallback((flight: SelectedFlight | null) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_SELECTED_FLIGHT', payload: flight })
    
    // When flight is removed from a combo booking, update booking type to 'hotel' only
    // if there's still a hotel but no flight
    if (!flight && state.bookingType === 'both' && state.selectedHotel) {
      dispatch({ type: 'SET_BOOKING_TYPE', payload: 'hotel' })
      dispatch({ type: 'UPDATE_BOOKING_DATA', payload: { 
        bookingType: 'HOTEL' as 'FLIGHT' | 'HOTEL' | 'COMBO',
        flightSelection: undefined
      }})
    }
    // When flight is added back to a hotel-only booking, update booking type to 'both'
    else if (flight && state.bookingType === 'hotel' && state.selectedHotel) {
      dispatch({ type: 'SET_BOOKING_TYPE', payload: 'both' })
      dispatch({ type: 'UPDATE_BOOKING_DATA', payload: { 
        bookingType: 'COMBO' as 'FLIGHT' | 'HOTEL' | 'COMBO'
      }})
    }
  }, [state.bookingType, state.selectedHotel])

  const setSelectedHotel = useCallback((hotel: SelectedHotel | null) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_SELECTED_HOTEL', payload: hotel })
    
    // When hotel is removed from a combo booking, update booking type to 'flight' only
    // if there's still a flight but no hotel
    if (!hotel && state.bookingType === 'both' && state.selectedFlight) {
      dispatch({ type: 'SET_BOOKING_TYPE', payload: 'flight' })
      dispatch({ type: 'UPDATE_BOOKING_DATA', payload: { 
        bookingType: 'FLIGHT' as 'FLIGHT' | 'HOTEL' | 'COMBO',
        hotelSelection: undefined
      }})
    }
    // When hotel is added back to a flight-only booking, update booking type to 'both'
    else if (hotel && state.bookingType === 'flight' && state.selectedFlight) {
      dispatch({ type: 'SET_BOOKING_TYPE', payload: 'both' })
      dispatch({ type: 'UPDATE_BOOKING_DATA', payload: { 
        bookingType: 'COMBO' as 'FLIGHT' | 'HOTEL' | 'COMBO'
      }})
    }
  }, [state.bookingType, state.selectedFlight])

  const setFlightDetails = useCallback((details: FlightBookingDetails | null) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_FLIGHT_DETAILS', payload: details })
  }, [])

  const setHotelDetails = useCallback((details: HotelBookingDetails | null) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_HOTEL_DETAILS', payload: details })
  }, [])

  const nextStep = useCallback(() => {
    if (!mountedRef.current) return
    const steps: BookingStep[] = ['selection', 'passengers', 'review', 'payment', 'confirmation']
    const currentIndex = steps.indexOf(state.step)
    if (currentIndex < steps.length - 1) {
      dispatch({ type: 'SET_STEP', payload: steps[currentIndex + 1] as BookingStep })
    }
  }, [state.step])

  const prevStep = useCallback(() => {
    if (!mountedRef.current) return
    const steps: BookingStep[] = ['selection', 'passengers', 'review', 'payment', 'confirmation']
    const currentIndex = steps.indexOf(state.step)
    if (currentIndex > 0) {
      dispatch({ type: 'SET_STEP', payload: steps[currentIndex - 1] as BookingStep })
    }
  }, [state.step])

  const setError = useCallback((error: string | null) => {
    if (!mountedRef.current) return
    dispatch({ type: 'SET_ERROR', payload: error })
  }, [])

  const stopStatusPolling = useCallback(() => {
    if (statusPollingRef.current) {
      clearTimeout(statusPollingRef.current)
      statusPollingRef.current = null
    }
    dispatch({ type: 'SET_STATUS_POLLING', payload: false })
  }, [])

  // Enhanced cleanup that also clears any pending promises
  const enhancedStopStatusPolling = useCallback(() => {
    if (statusPollingRef.current) {
      clearTimeout(statusPollingRef.current)
      statusPollingRef.current = null
    }
    dispatch({ type: 'SET_STATUS_POLLING', payload: false })
  }, [])

  const pollBookingStatus = useCallback(async (bookingIdParam?: string) => {
    // Check if component is still mounted before proceeding
    if (!mountedRef.current) return
    
    const bookingId = bookingIdParam || state.bookingResponse?.bookingId
    if (!bookingId) return

    dispatch({ type: 'SET_STATUS_POLLING', payload: true })

    try {
      const statusResponse = await bookingService.getStatus(bookingId)
      
      // Check again if component is still mounted before updating state
      if (!mountedRef.current) return
      
      dispatch({ type: 'SET_BOOKING_STATUS', payload: statusResponse })

      const pendingStatuses = new Set(['VALIDATION_PENDING', 'PENDING', 'PAYMENT_PENDING'])
      const successStatuses = new Set(['CONFIRMED', 'PAID'])
      const failureStatuses = new Set(['FAILED', 'PAYMENT_FAILED', 'CANCELLED', 'CANCELED', 'VALIDATION_FAILED', 'REJECTED'])

      if (successStatuses.has(statusResponse.status)) {
        enhancedStopStatusPolling()
        dispatch({ type: 'SET_STEP', payload: 'confirmation' })
      } else if (failureStatuses.has(statusResponse.status)) {
        enhancedStopStatusPolling()
        const rawMessage = statusResponse.message || 'Booking failed. Please try again.'
        const lowerMessage = rawMessage.toLowerCase()
        const userFriendlyMessage = lowerMessage.includes('hotel room') || lowerMessage.includes('hotel reservation')
          ? 'Phòng khách sạn bạn chọn không còn trống. Vui lòng chọn lựa chọn khác.'
          : rawMessage

        dispatch({ type: 'SET_ERROR', payload: userFriendlyMessage })
        toast({
          title: 'Booking Failed',
          description: userFriendlyMessage,
          variant: 'destructive',
        })
      } else if (pendingStatuses.has(statusResponse.status)) {
        // Check if we should continue polling
        if (statusPollingRef.current) {
          clearTimeout(statusPollingRef.current)
        }
        // Only set timeout if component is still mounted
        if (mountedRef.current) {
          statusPollingRef.current = setTimeout(() => {
            void pollBookingStatus(bookingId)
          }, 5000)
        }
      } else {
        // Unknown status - stop polling to avoid infinite loop
        enhancedStopStatusPolling()
      }
    } catch (error) {
      console.error('Booking status polling error:', error)
      // Check if component is still mounted before updating state
      if (!mountedRef.current) return
      enhancedStopStatusPolling()
      dispatch({ type: 'SET_ERROR', payload: 'Unable to retrieve booking status. Please try again.' })
    }
  }, [state.bookingResponse?.bookingId, enhancedStopStatusPolling])

  const refreshBookingStatus = useCallback(async () => {
    await pollBookingStatus()
  }, [pollBookingStatus])

  const createBooking = useCallback(async () => {
    // Check if component is still mounted before proceeding
    if (!mountedRef.current) return

    const bookingType = state.bookingData.bookingType
    const flightSelection = state.bookingData.flightSelection
    const hotelSelection = state.bookingData.hotelSelection

    const missingFlight = (bookingType === 'FLIGHT' || bookingType === 'COMBO') && !flightSelection
    const missingHotel = (bookingType === 'HOTEL' || bookingType === 'COMBO') && !hotelSelection

    if (!bookingType || missingFlight || missingHotel) {
      dispatch({ type: 'SET_ERROR', payload: 'Missing required booking information' })
      return
    }

    const derivedTotal = (() => {
      if (bookingType === 'FLIGHT') {
        return flightSelection?.totalFlightPrice ?? 0
      }
      if (bookingType === 'HOTEL') {
        return hotelSelection?.totalRoomPrice ?? 0
      }
      if (bookingType === 'COMBO') {
        const flightTotal = flightSelection?.totalFlightPrice ?? 0
        const hotelTotal = hotelSelection?.totalRoomPrice ?? 0
        const discount = state.bookingData.comboDiscount ?? 0
        return Math.max(flightTotal + hotelTotal - discount, 0)
      }
      return 0
    })()

    enhancedStopStatusPolling()
    dispatch({ type: 'SET_LOADING', payload: true })
    dispatch({ type: 'SET_ERROR', payload: null })

    try {
      const request: StorefrontBookingRequest = {
        bookingType,
        totalAmount: state.bookingData.totalAmount && state.bookingData.totalAmount > 0
          ? state.bookingData.totalAmount
          : derivedTotal,
        currency: state.bookingData.currency || 'VND',
        notes: state.bookingData.notes,
      }

      if (bookingType === 'FLIGHT' || bookingType === 'COMBO') {
        request.flightSelection = flightSelection
      }
      if (bookingType === 'HOTEL' || bookingType === 'COMBO') {
        request.hotelSelection = hotelSelection
      }
      if (bookingType === 'COMBO' && typeof state.bookingData.comboDiscount === 'number') {
        request.comboDiscount = state.bookingData.comboDiscount
      }

      const response = await bookingService.create(request)
      
      // Check if component is still mounted before updating state
      if (!mountedRef.current) return
      
      if (response.error) {
        throw new Error(response.error)
      }

      dispatch({ type: 'SET_BOOKING_RESPONSE', payload: response })
      dispatch({ type: 'SET_BOOKING_STATUS', payload: null })
      dispatch({ type: 'SET_STEP', payload: 'payment' })
      if (response.bookingId) {
        if (statusPollingRef.current) {
          clearTimeout(statusPollingRef.current)
        }
        // Only start polling if component is still mounted
        if (mountedRef.current) {
          void pollBookingStatus(response.bookingId)
        }
      }
      
      toast({
        title: "Booking Created",
        description: "Your booking has been successfully created.",
      })
    } catch (error: any) {
      console.error('Booking creation error:', error)
      // Check if component is still mounted before updating state
      if (!mountedRef.current) return
      
      const errorMessage = error.message || 'Failed to create booking. Please try again.'
      dispatch({ type: 'SET_ERROR', payload: errorMessage })
      
      toast({
        title: "Booking Failed",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      // Check if component is still mounted before updating state
      if (!mountedRef.current) return
      dispatch({ type: 'SET_LOADING', payload: false })
    }
  }, [state.bookingData, toast, pollBookingStatus, enhancedStopStatusPolling])

  const resetBooking = useCallback(() => {
    enhancedStopStatusPolling()
    dispatch({ type: 'RESET_BOOKING' })
  }, [enhancedStopStatusPolling])

  const cancelInFlightBooking = useCallback(async () => {
    const bookingId = state.bookingResponse?.bookingId
    if (!bookingId) {
      resetBooking()
      return
    }

    try {
      await bookingService.cancel(bookingId, 'User cancelled before completion')
      await pollBookingStatus(bookingId)
    } catch (error) {
      console.error('Booking cancellation error:', error)
    } finally {
      resetBooking()
    }
  }, [pollBookingStatus, resetBooking, state.bookingResponse?.bookingId])

  const resumeBooking = useCallback(async (payload: ResumeBookingPayload) => {
    const booking = payload.booking
    if (!booking) {
      return
    }

    const parseProductDetails = (json?: string | null) => {
      if (!json) return null
      try {
        return JSON.parse(json)
      } catch (error) {
        console.warn('Failed to parse stored booking product details', error)
        return null
      }
    }

    const parsedDetails = payload.productDetails ?? parseProductDetails(booking.productDetailsJson)
    const totalAmount = typeof booking.totalAmount === 'string'
      ? Number(booking.totalAmount)
      : Number(booking.totalAmount ?? 0)
    const currency = booking.currency ?? 'VND'

    dispatch({ type: 'RESET_BOOKING' })

    const contextType: BookingType = booking.bookingType === 'COMBO'
      ? 'both'
      : booking.bookingType === 'FLIGHT'
        ? 'flight'
        : 'hotel'

    setBookingType(contextType)

    const flightDetails = booking.bookingType === 'COMBO'
      ? (parsedDetails as ComboBookingDetails | null)?.flightDetails ?? null
      : booking.bookingType === 'FLIGHT'
        ? (parsedDetails as FlightBookingDetails | null)
        : null

    const hotelDetails = booking.bookingType === 'COMBO'
      ? (parsedDetails as ComboBookingDetails | null)?.hotelDetails ?? null
      : booking.bookingType === 'HOTEL'
        ? (parsedDetails as HotelBookingDetails | null)
        : null

    const flightSelection = flightDetails ? {
      flightId: String(flightDetails.flightId),
      scheduleId: flightDetails.scheduleId ?? undefined,
      fareId: flightDetails.fareId ?? undefined,
      seatClass: flightDetails.seatClass,
      departureDateTime: flightDetails.departureDateTime,
      arrivalDateTime: flightDetails.arrivalDateTime,
      passengerCount: flightDetails.passengerCount,
      passengers: flightDetails.passengers,
      selectedSeats: flightDetails.selectedSeats,
      additionalServices: flightDetails.additionalServices,
      specialRequests: flightDetails.specialRequests,
      pricePerPassenger: flightDetails.pricePerPassenger,
      totalFlightPrice: flightDetails.totalFlightPrice,
      airlineLogo: flightDetails.airlineLogo,
      originAirportName: flightDetails.originAirportName,
      destinationAirportName: flightDetails.destinationAirportName,
      originAirportImage: flightDetails.originAirportImage,
      destinationAirportImage: flightDetails.destinationAirportImage,
    } as StorefrontFlightSelection : undefined

    const hotelSelection = hotelDetails ? {
      hotelId: hotelDetails.hotelId,
      roomTypeId: String(hotelDetails.roomTypeId ?? ''),
      roomId: hotelDetails.roomId ?? undefined,
      roomAvailabilityId: (hotelDetails as any).roomAvailabilityId ?? undefined,
      checkInDate: hotelDetails.checkInDate,
      checkOutDate: hotelDetails.checkOutDate,
      numberOfNights: hotelDetails.numberOfNights,
      numberOfRooms: hotelDetails.numberOfRooms,
      numberOfGuests: hotelDetails.numberOfGuests,
      guests: hotelDetails.guests,
      pricePerNight: hotelDetails.pricePerNight,
      totalRoomPrice: hotelDetails.totalRoomPrice,
      bedType: hotelDetails.bedType,
      amenities: hotelDetails.amenities,
      additionalServices: hotelDetails.additionalServices,
      specialRequests: hotelDetails.specialRequests,
      cancellationPolicy: hotelDetails.cancellationPolicy,
      hotelImage: hotelDetails.hotelImage,
      roomImage: hotelDetails.roomImage,
      roomImages: hotelDetails.roomImages,
    } as StorefrontHotelSelection : undefined

    updateBookingData({
      bookingType: booking.bookingType,
      totalAmount,
      currency,
      flightSelection,
      hotelSelection,
      comboDiscount: booking.bookingType === 'COMBO'
        ? (parsedDetails as ComboBookingDetails | null)?.comboDiscount ?? undefined
        : undefined,
    })

    setFlightDetails(flightDetails ?? null)
    setHotelDetails(hotelDetails ?? null)

    if (booking.bookingType === 'FLIGHT' || booking.bookingType === 'COMBO') {
      if (flightDetails) {
        setSelectedFlight({
          flightId: flightDetails.flightId,
          flightNumber: flightDetails.flightNumber,
          airline: flightDetails.airline,
          origin: flightDetails.originAirport,
          destination: flightDetails.destinationAirport,
          originLatitude: flightDetails.originLatitude,
          originLongitude: flightDetails.originLongitude,
          destinationLatitude: flightDetails.destinationLatitude,
          destinationLongitude: flightDetails.destinationLongitude,
          departureTime: flightDetails.departureDateTime,
          arrivalTime: flightDetails.arrivalDateTime,
          duration: undefined,
          price: flightDetails.totalFlightPrice,
          currency,
          seatClass: flightDetails.seatClass,
          scheduleId: flightDetails.scheduleId,
          fareId: flightDetails.fareId,
          logo: flightDetails.airlineLogo,
          airlineLogo: flightDetails.airlineLogo,
          originAirportName: flightDetails.originAirportName ?? flightDetails.originAirport,
          destinationAirportName: flightDetails.destinationAirportName ?? flightDetails.destinationAirport,
          originAirportImage: flightDetails.originAirportImage,
          destinationAirportImage: flightDetails.destinationAirportImage,
        })
      } else {
        setSelectedFlight(null)
      }
    } else {
      setSelectedFlight(null)
    }

    if (booking.bookingType === 'HOTEL' || booking.bookingType === 'COMBO') {
      if (hotelDetails) {
        setSelectedHotel({
          id: hotelDetails.hotelId,
          name: hotelDetails.hotelName,
          address: hotelDetails.hotelAddress,
          city: hotelDetails.city,
          country: hotelDetails.country,
          hotelLatitude: hotelDetails.hotelLatitude,
          hotelLongitude: hotelDetails.hotelLongitude,
          rating: hotelDetails.starRating,
          roomTypeId: String(hotelDetails.roomTypeId ?? ''),
          roomType: hotelDetails.roomType,
          roomName: hotelDetails.roomName,
          price: hotelDetails.pricePerNight,
          pricePerNight: hotelDetails.pricePerNight,
          totalPrice: hotelDetails.totalRoomPrice,
          currency,
          amenities: hotelDetails.amenities ?? [],
          image: hotelDetails.hotelImage,
          images: hotelDetails.roomImages ?? (hotelDetails.hotelImage ? [hotelDetails.hotelImage] : undefined),
          roomImages: hotelDetails.roomImages,
          checkInDate: hotelDetails.checkInDate,
          checkOutDate: hotelDetails.checkOutDate,
          guests: hotelDetails.numberOfGuests,
          rooms: hotelDetails.numberOfRooms,
          nights: hotelDetails.numberOfNights,
        })
      } else {
        setSelectedHotel(null)
      }
    } else {
      setSelectedHotel(null)
    }

    const response: StorefrontBookingResponse = {
      bookingId: booking.bookingId,
      bookingReference: booking.bookingReference,
      sagaId: booking.sagaId ?? booking.bookingId,
      status: booking.status,
    }

    dispatch({ type: 'SET_BOOKING_RESPONSE', payload: response })
    dispatch({ type: 'SET_BOOKING_STATUS', payload: null })

    // Set the correct step based on booking status (checking both status and sagaState)
    // PAYMENT_PENDING can be in either status or sagaState field
    const normalizedStatus = booking.status?.toUpperCase()
    const normalizedSagaState = booking.sagaState?.toUpperCase()
    const requiresPayment = normalizedStatus === 'PAYMENT_PENDING' || normalizedSagaState === 'PAYMENT_PENDING' || normalizedStatus === 'PENDING'
    const nextStep = requiresPayment ? 'payment' : 'review'
    setStep(nextStep as BookingStep)

    if (booking.bookingId) {
      await pollBookingStatus(booking.bookingId)
    }
  }, [pollBookingStatus, setBookingType, setSelectedFlight, setSelectedHotel, setStep, updateBookingData, stopStatusPolling])

  const value = {
    ...state,
    setBookingType,
    updateBookingData,
    nextStep,
    prevStep,
    createBooking,
    resetBooking,
    setError,
    setStep,
    setSelectedFlight,
    setSelectedHotel,
    setFlightDetails,
    setHotelDetails,
    refreshBookingStatus,
    cancelInFlightBooking,
    resumeBooking,
  }

  useEffect(() => {
    return () => {
      if (statusPollingRef.current) {
        clearTimeout(statusPollingRef.current)
      }
    }
  }, [])

  return (
    <BookingContext.Provider value={value}>
      {children}
    </BookingContext.Provider>
  )
}

// Hook
export function useBooking() {
  const context = useContext(BookingContext)
  if (context === undefined) {
    throw new Error('useBooking must be used within a BookingProvider')
  }
  return context
}

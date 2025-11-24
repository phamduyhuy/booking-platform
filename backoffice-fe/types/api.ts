export interface ApiResponse<T> {
  data: T
  message?: string
  success: boolean
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

export interface MediaResponse {
  id: number
  mediaId: number
  publicId: string
  url: string
  secureUrl: string
  isPrimary: boolean
  displayOrder: number
}

export interface Aircraft {
  aircraftId: number
  model: string
  manufacturer?: string
  capacityEconomy?: number
  capacityBusiness?: number
  capacityFirst?: number
  totalCapacity?: number
  registrationNumber?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
  media?: MediaResponse[]     // For creating/updating aircraft - complete media info
}

export interface Airline {
  airlineId: number
  name: string
  iataCode: string
  country?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
  createdBy?: string
  updatedBy?: string
  images?: string[]     // Array of image URLs/IDs from backend
  totalFlights?: number
  activeFlights?: number
  totalRoutes?: number
  status?: string
  media?: MediaResponse[]
}

export interface Airport {
  airportId: number
  name: string
  iataCode: string
  city: string
  country: string
  timezone?: string
  latitude?: number
  longitude?: number
  isActive: boolean
  createdAt: string
  updatedAt: string
  media?: MediaResponse[]     // For creating/updating airports - complete media info
}

export interface Flight {
  id?: number
  flightId?: number
  flightNumber: string
  airline?: Airline
  airlineId?: number
  airlineName?: string
  airlineIataCode?: string
  departureAirport?: Airport
  departureAirportId?: number
  departureAirportName?: string
  departureAirportIataCode?: string
  departureAirportCity?: string
  departureAirportCountry?: string
  arrivalAirport?: Airport
  arrivalAirportId?: number
  arrivalAirportName?: string
  arrivalAirportIataCode?: string
  arrivalAirportCity?: string
  arrivalAirportCountry?: string
  aircraftType?: string
  aircraft?: Aircraft
  status?: "ACTIVE" | "CANCELLED" | "DELAYED" | "ON_TIME" | "SCHEDULED"
  isActive?: boolean
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
  // Note: schedules array intentionally omitted to prevent circular references
  schedules?: FlightSchedule[]  // Only include when specifically needed
  fares?: FlightFare[]
  totalSchedules?: number
  activeSchedules?: number
  totalBookings?: number
  images?: string[]
  primaryImage?: string
  hasMedia?: boolean
  mediaCount?: number
  media?: MediaResponse[]     // For creating/updating flights - complete media info
}

export interface Hotel {
  id: number
  name: string
  description: string
  address: string
  city: string
  country: string
  starRating: number
  availableRooms: number
  minPrice: number
  maxPrice?: number
  averageRating?: number
  totalReviews?: number
  amenities?: Amenity[]
  media?: MediaResponse[]     // For creating/updating hotels - complete media info
  latitude?: number
  longitude?: number
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface RoomType {
  id: number
  name: string
  description: string
  capacityAdults?: number
  basePrice?: number
  media?: MediaResponse[]     // For creating/updating room types - complete media info
  createdAt?: string
  updatedAt?: string
}

export interface RoomAvailabilityDay {
  date: string
  totalInventory: number
  totalReserved: number
  remaining: number
  autoCalculated?: boolean
}

export interface RoomAvailabilityResponse {
  hotelId: number
  roomTypeId: number
  roomTypeName: string
  startDate: string
  endDate: string
  activeRoomCount: number
  availability: RoomAvailabilityDay[]
}

export interface RoomAvailabilityUpdate {
  date: string
  totalInventory: number
  totalReserved: number
}

export interface RoomTypeInheritance {
  id: number
  name: string
  description: string
  basePrice?: number
  media?: MediaResponse[]
  primaryImage?: MediaResponse
  hasMedia: boolean
  mediaCount: number
}

export interface Amenity {
  id: number
  name: string
  iconUrl?: string
  isActive: boolean
  displayOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface Booking {
  bookingId: string
  bookingReference: string
  userId: string
  totalAmount: number
  currency: string
  status: "VALIDATION_PENDING" | "PENDING" | "CONFIRMED" | "PAYMENT_PENDING" | "PAID" | "PAYMENT_FAILED" | "CANCELLED" | "FAILED" | "VALIDATION_FAILED"
  bookingType: "FLIGHT" | "HOTEL" | "COMBO"
  sagaState: string
  sagaId: string
  confirmationNumber?: string
  cancelledAt?: number | null
  cancellationReason?: string | null
  compensationReason?: string | null
  productDetailsJson: string
  notes?: string | null
  bookingSource: "STOREFRONT" | "BACKOFFICE" | "API"
  createdAt: number
  createdBy: string
  updatedAt: number
  updatedBy: string
  deletedAt?: number | null
  deletedBy?: string | null
  deleted: boolean
}

export interface Customer {
  id: string
  name: string
  email: string
  phone: string
  dateOfBirth?: string
  nationality?: string
  tier: "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
  totalBookings: number
  totalSpent: number
  status: "ACTIVE" | "INACTIVE" | "BANNED"
  createdAt: string
  lastLoginAt?: string
}

export interface CustomerStatistics {
  totalCustomers: number
  activeCustomers: number
  newCustomersThisMonth: number
}

export interface FlightFare {
  fareId: string
  scheduleId: string
  fareClass: "ECONOMY" | "PREMIUM_ECONOMY" | "BUSINESS" | "FIRST"
  price: number
  availableSeats: number
}

export interface FlightFareCreateRequest {
  scheduleId: string
  fareClass: "ECONOMY" | "PREMIUM_ECONOMY" | "BUSINESS" | "FIRST"
  price: number
  availableSeats: number
}

export interface FlightFareUpdateRequest {
  fareClass?: "ECONOMY" | "PREMIUM_ECONOMY" | "BUSINESS" | "FIRST"
  price?: number
  availableSeats?: number
}

export interface FlightFareCalculationRequest {
  scheduleIds: string[]
  fareClass: string
  departureDate: string
  passengerCount: number
  basePrice?: number
  aircraftType?: string
}

export interface FlightFareCalculationResult {
  scheduleId: string
  flightNumber: string
  origin: string
  destination: string
  aircraftType: string
  fareClass: string
  calculatedPrice: number
  availableSeats: number
  currency: string
  demandMultiplier: number
  timeMultiplier: number
  seasonalityMultiplier: number
  fareClassMultiplier: number
}

export interface FlightSchedule {
  scheduleId: string
  flightId: number
  departureTime: string
  arrivalTime: string
  aircraftType?: string
  aircraftId?: number
  status: "SCHEDULED" | "ACTIVE" | "DELAYED" | "CANCELLED" | "COMPLETED"
  flight?: Flight
  aircraft?: Aircraft
  durationMinutes?: number
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

export interface FlightScheduleCreateRequest {
  flightId: number
  departureTime: string
  arrivalTime: string
  aircraftId: number
  status?: "SCHEDULED" | "ACTIVE" | "DELAYED" | "CANCELLED" | "COMPLETED"
}

export interface FlightScheduleUpdateRequest {
  departureTime?: string
  arrivalTime?: string
  aircraftId?: number
  status?: "SCHEDULED" | "ACTIVE" | "DELAYED" | "CANCELLED" | "COMPLETED"
}

// Payment related interfaces
export interface Payment {
  paymentId: string
  paymentReference: string
  bookingId: string
  userId: string
  customerId?: string
  sagaId: string
  sagaStep?: string
  amount: number
  currency: string
  description?: string
  status: PaymentStatus
  methodType: PaymentMethodType
  provider: PaymentProvider
  paymentMethodId?: string
  gatewayTransactionId?: string
  gatewayResponse?: string
  gatewayStatus?: string
  metadata?: string
  ipAddress?: string
  userAgent?: string
  createdAt: number
  updatedAt: number
  createdBy: string
  updatedBy: string
  deletedAt?: number | null
  deletedBy?: string | null
  deleted: boolean
  transactions?: PaymentTransaction[]
}

export interface PaymentTransaction {
  transactionId: string
  paymentId: string
  transactionReference: string
  transactionType: PaymentTransactionType
  amount: number
  currency: string
  status: PaymentStatus
  provider: PaymentProvider
  sagaId: string
  sagaStep?: string
  originalTransactionId?: string
  gatewayTransactionId?: string
  gatewayResponse?: string
  gatewayStatus?: string
  description?: string
  metadata?: string
  processedAt?: number
  confirmedAt?: number
  createdAt: number
  updatedAt: number
  createdBy: string
  updatedBy: string
}

export interface PaymentMethod {
  paymentMethodId: string
  userId: string
  methodType: PaymentMethodType
  provider: PaymentProvider
  gatewayMethodId?: string
  last4?: string
  cardType?: string
  expiryMonth?: number
  expiryYear?: number
  holderName?: string
  isDefault: boolean
  isActive: boolean
  metadata?: string
  createdAt: number
  updatedAt: number
}

export interface PaymentSagaLog {
  logId: string
  sagaId: string
  paymentId: string
  step: string
  status: string
  request?: string
  response?: string
  errorMessage?: string
  retryCount: number
  createdAt: number
}

export type PaymentStatus = 
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "CONFIRMED"
  | "FAILED"
  | "DECLINED"
  | "CANCELLED"
  | "REFUND_PENDING"
  | "REFUND_PROCESSING"
  | "REFUND_COMPLETED"
  | "REFUND_FAILED"
  | "TIMEOUT"
  | "ERROR"
  | "COMPENSATION_PENDING"
  | "COMPENSATION_COMPLETED"
  | "COMPENSATION_FAILED"

export type PaymentMethodType =
  | "CREDIT_CARD"
  | "DEBIT_CARD"
  | "BANK_TRANSFER"
  | "E_WALLET"
  | "QR_CODE"
  | "CASH"

export type PaymentProvider =
  | "STRIPE"
  | "VIETQR"
  | "MOMO"
  | "ZALOPAY"
  | "VNPAY"
  | "PAYPAL"
  | "MANUAL"

export type PaymentTransactionType =
  | "PAYMENT"
  | "REFUND"
  | "CHARGEBACK"
  | "COMPENSATION"

export interface PaymentProcessRequest {
  bookingId: string
  amount: number
  currency?: string
  description?: string
  methodType: PaymentMethodType
  provider: PaymentProvider
  paymentMethodData?: Record<string, any>
  additionalData?: Record<string, any>
}

export interface PaymentRefundRequest {
  paymentId: string
  amount?: number
  reason?: string
}

export interface PaymentFilters {
  search?: string
  status?: PaymentStatus
  provider?: PaymentProvider
  methodType?: PaymentMethodType
  bookingId?: string
  userId?: string
  dateFrom?: string
  dateTo?: string
  amountFrom?: number
  amountTo?: number
  page?: number
  size?: number
  sort?: string
  direction?: "ASC" | "DESC"
}

export interface PaymentStats {
  totalPayments: number
  totalAmount: number
  successfulPayments: number
  failedPayments: number
  pendingPayments: number
  refundedAmount: number
  averageAmount: number
  conversionRate: number
  topProviders: Array<{
    provider: PaymentProvider
    count: number
    amount: number
  }>
  statusDistribution: Array<{
    status: PaymentStatus
    count: number
    percentage: number
  }>
  monthlyTrends: Array<{
    month: string
    totalAmount: number
    totalCount: number
    successRate: number
  }>
}

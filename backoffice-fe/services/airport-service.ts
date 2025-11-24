import type { Airport, PaginatedResponse, ApiResponse } from "@/types/api"
import { apiClient } from "@/lib/api-client"

interface AirportCreateRequest {
  name: string
  iataCode: string
  city: string
  country: string
  timezone?: string
  latitude?: number
  longitude?: number
  images?: string[] // Array of publicIds from MediaSelector
  mediaPublicIds?: string[]
  featuredMediaUrl?: string | null
}

interface AirportUpdateRequest {
  name?: string
  iataCode?: string
  city?: string
  country?: string
  timezone?: string
  latitude?: number
  longitude?: number
  images?: string[] // Array of publicIds from MediaSelector
  mediaPublicIds?: string[]
  featuredMediaUrl?: string | null
}

export class AirportService {
  private static readonly BASE_PATH = "/api/flights/backoffice/airports"

  /**
   * Get airports with pagination and filtering
   */
  static async getAirports(params?: {
    page?: number
    size?: number
    search?: string
    city?: string
    country?: string
  }): Promise<PaginatedResponse<Airport>> {
    try {
      const searchParams = new URLSearchParams()
      
      if (params?.page !== undefined) searchParams.append("page", params.page.toString())
      if (params?.size !== undefined) searchParams.append("size", params.size.toString())
      if (params?.search) searchParams.append("search", params.search)
      if (params?.city) searchParams.append("city", params.city)
      if (params?.country) searchParams.append("country", params.country)

      const url = `${this.BASE_PATH}${searchParams.toString() ? `?${searchParams.toString()}` : ""}`
      const response: ApiResponse<any> = await apiClient.get(url)
      
      // Transform backend data to match frontend Airport interface
      const transformedContent = response.data?.content?.map((airport: any) => ({
        airportId: airport.airportId,
        name: airport.name,
        iataCode: airport.iataCode,
        city: airport.city,
        country: airport.country,
        timezone: airport.timezone,
        latitude: airport.latitude,
        longitude: airport.longitude,
        isActive: airport.isActive,
        createdAt: airport.createdAt,
        updatedAt: airport.updatedAt,
        media: airport.media
      })) || [];

      // Transform only the pagination structure to match PaginatedResponse
      return {
        content: transformedContent,
        totalElements: response.data?.totalElements || 0,
        totalPages: response.data?.totalPages || 0,
        size: response.data?.pageSize || 0,
        number: response.data?.currentPage || 0,
        first: !response.data?.hasPrevious,
        last: !response.data?.hasNext,
      }
    } catch (error) {
      console.error("Error fetching airports:", error)
      throw error
    }
  }

  /**
   * Get all active airports for dropdown
   */
  static async getActiveAirports(): Promise<Airport[]> {
    try {
      // Use paginated endpoint with large size to get all airports
      const paginatedResponse = await this.getAirports({ page: 0, size: 1000 })
      return paginatedResponse.content.filter(airport => airport.isActive !== false)
    } catch (error) {
      console.error("Error fetching active airports:", error)
      return []
    }
  }

  /**
   * Get single airport by ID
   */
  static async getAirport(id: string | number): Promise<Airport> {
    try {
      const response: ApiResponse<Airport> = await apiClient.get(`${this.BASE_PATH}/${id}`)
      return response.data
    } catch (error) {
      console.error(`Error fetching airport ${id}:`, error)
      throw error
    }
  }

  /**
   * Create a new airport
   */
  static async createAirport(airport: AirportCreateRequest): Promise<Airport> {
    try {
      const response: ApiResponse<Airport> = await apiClient.post(this.BASE_PATH, airport)
      return response.data
    } catch (error) {
      console.error("Error creating airport:", error)
      throw error
    }
  }

  /**
   * Update an existing airport
   */
  static async updateAirport(id: string | number, airport: AirportUpdateRequest): Promise<Airport> {
    try {
      const response: ApiResponse<Airport> = await apiClient.put(`${this.BASE_PATH}/${id}`, airport)
      return response.data
    } catch (error) {
      console.error(`Error updating airport ${id}:`, error)
      throw error
    }
  }

  /**
   * Delete an airport
   */
  static async deleteAirport(id: string | number): Promise<void> {
    try {
      await apiClient.delete(`${this.BASE_PATH}/${id}`)
    } catch (error) {
      console.error(`Error deleting airport ${id}:`, error)
      throw error
    }
  }
}

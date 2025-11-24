import type { Hotel, PaginatedResponse, MediaResponse, ApiResponse } from "@/types/api"
import { apiClient } from "@/lib/api-client"

export class HotelService {
  private static readonly BASE_PATH = "/api/hotels/backoffice/hotels"

  static async getHotels(params?: {
    page?: number
    size?: number
    search?: string
    city?: string
  }): Promise<PaginatedResponse<Hotel>> {
    const queryParams = new URLSearchParams()
    
    if (params?.page !== undefined) queryParams.append('page', params.page.toString())
    if (params?.size !== undefined) queryParams.append('size', params.size.toString())
    if (params?.search) queryParams.append('search', params.search)
    if (params?.city) queryParams.append('city', params.city)
    
    const queryString = queryParams.toString()
    const url = `${this.BASE_PATH}${queryString ? `?${queryString}` : ''}`
    
    // The backend returns ApiResponse<PaginatedResponse<Hotel>>, so we need to extract the data field
    const response = await apiClient.get<ApiResponse<PaginatedResponse<Hotel>>>(url);
    return response.data; // Extract the 'data' field from the ApiResponse wrapper
  }

  static async getHotel(id: number): Promise<Hotel> {
    // The backend returns ApiResponse<Hotel>, so we need to extract the data field
    const response = await apiClient.get<ApiResponse<Hotel>>(`${this.BASE_PATH}/${id}`);
    return response.data; // Extract the 'data' field from the ApiResponse wrapper
  }

  static async createHotel(hotel: Omit<Hotel, "id" | "createdAt" | "updatedAt" | "availableRooms" | "minPrice"> & { media?: MediaResponse[] }): Promise<Hotel> {
    // Remove images field if it exists and prepare data for backend
    const { images, ...hotelData } = hotel as any
    
    const response = await apiClient.post<ApiResponse<Hotel>>(this.BASE_PATH, hotelData);
    return response.data; // Extract the 'data' field from the ApiResponse wrapper
  }

  static async updateHotel(id: number, hotel: Partial<Hotel> & { media?: MediaResponse[] }): Promise<Hotel> {
    // Remove images field if it exists and prepare data for backend
    const { images, ...hotelData } = hotel as any
    
    const response = await apiClient.put<ApiResponse<Hotel>>(`${this.BASE_PATH}/${id}`, hotelData);
    return response.data; // Extract the 'data' field from the ApiResponse wrapper
  }

  static async deleteHotel(id: number): Promise<void> {
    await apiClient.delete<ApiResponse<void>>(`${this.BASE_PATH}/${id}`);
    // No return value needed for delete operation
  }

  static async updateHotelAmenities(hotelId: number, amenityIds: number[]): Promise<Hotel> {
    const response = await apiClient.put<ApiResponse<Hotel>>(`${this.BASE_PATH}/${hotelId}/amenities`, {
      amenityIds
    });
    return response.data; // Extract the 'data' field from the ApiResponse wrapper
  }
  static async getHotelStatistics(): Promise<HotelStatistics> {
    const response = await apiClient.get<ApiResponse<HotelStatistics>>(`${this.BASE_PATH}/statistics`);
    return response.data;
  }
}

export interface HotelStatistics {
  totalHotels: number;
  activeHotels: number;
  totalRooms: number;
  availableRooms: number;
  bookedRooms: number;
  maintenanceRooms: number;
}

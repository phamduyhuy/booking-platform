import { apiClient } from "@/lib/api-client";
import type { 
  FlightSchedule, 
  FlightScheduleCreateRequest, 
  FlightScheduleUpdateRequest, 
  PaginatedResponse, 
  ApiResponse 
} from "@/types/api";

export interface FlightScheduleListParams {
  page?: number;
  size?: number;
  search?: string; // Unified search for flight number and airports
  date?: string; // YYYY-MM-DD format
  departureAirportId?: number; // Filter by departure airport
  arrivalAirportId?: number; // Filter by arrival airport
}

export interface FlightScheduleStatistics {
  totalSchedules: number;
  scheduledCount: number;
  activeCount: number;
  delayedCount: number;
  cancelledCount: number;
  completedCount: number;
}

export class FlightScheduleService {
  private static readonly BASE_PATH = "/api/flights/backoffice/schedules";

  /**
   * Clean up flight schedule data to remove circular references
   */
  private static cleanScheduleData(schedule: any): FlightSchedule {
    const cleaned: FlightSchedule = {
      scheduleId: schedule.scheduleId,
      flightId: schedule.flightId,
      departureTime: schedule.departureTime,
      arrivalTime: schedule.arrivalTime,
      aircraftType: schedule.aircraftType,
      aircraftId: schedule.aircraftId,
      status: schedule.status,
      durationMinutes: schedule.durationMinutes,
      createdAt: schedule.createdAt,
      updatedAt: schedule.updatedAt,
      createdBy: schedule.createdBy,
      updatedBy: schedule.updatedBy,
    };

    // Include flight data but remove nested schedules to prevent circular references
    if (schedule.flight) {
      cleaned.flight = {
        id: schedule.flight.id || schedule.flight.flightId,
        flightId: schedule.flight.flightId,
        flightNumber: schedule.flight.flightNumber,
        aircraftType: schedule.flight.aircraftType,
        status: schedule.flight.status,
        isActive: schedule.flight.isActive,
        airlineId: schedule.flight.airlineId,
        airlineName: schedule.flight.airlineName,
        airlineIataCode: schedule.flight.airlineIataCode,
        departureAirportId: schedule.flight.departureAirportId,
        departureAirportName: schedule.flight.departureAirportName,
        departureAirportIataCode: schedule.flight.departureAirportIataCode,
        departureAirportCity: schedule.flight.departureAirportCity,
        departureAirportCountry: schedule.flight.departureAirportCountry,
        arrivalAirportId: schedule.flight.arrivalAirportId,
        arrivalAirportName: schedule.flight.arrivalAirportName,
        arrivalAirportIataCode: schedule.flight.arrivalAirportIataCode,
        arrivalAirportCity: schedule.flight.arrivalAirportCity,
        arrivalAirportCountry: schedule.flight.arrivalAirportCountry,
        createdAt: schedule.flight.createdAt,
        updatedAt: schedule.flight.updatedAt,
        createdBy: schedule.flight.createdBy,
        updatedBy: schedule.flight.updatedBy,
        // Create simplified airport objects from the flat data
        departureAirport: schedule.flight.departureAirportId ? {
          airportId: schedule.flight.departureAirportId,
          name: schedule.flight.departureAirportName || '',
          iataCode: schedule.flight.departureAirportIataCode || '',
          city: schedule.flight.departureAirportCity || '',
          country: schedule.flight.departureAirportCountry || '',
          isActive: true,
          createdAt: '',
          updatedAt: ''
        } : undefined,
        arrivalAirport: schedule.flight.arrivalAirportId ? {
          airportId: schedule.flight.arrivalAirportId,
          name: schedule.flight.arrivalAirportName || '',
          iataCode: schedule.flight.arrivalAirportIataCode || '',
          city: schedule.flight.arrivalAirportCity || '',
          country: schedule.flight.arrivalAirportCountry || '',
          isActive: true,
          createdAt: '',
          updatedAt: ''
        } : undefined,
        // Explicitly exclude schedules and fares to prevent circular references
      };
    }

    // Include aircraft data if available
    if (schedule.aircraft) {
      cleaned.aircraft = {
        aircraftId: schedule.aircraft.aircraftId,
        model: schedule.aircraft.model,
        manufacturer: schedule.aircraft.manufacturer,
        capacityEconomy: schedule.aircraft.capacityEconomy,
        capacityBusiness: schedule.aircraft.capacityBusiness,
        capacityFirst: schedule.aircraft.capacityFirst,
        totalCapacity: schedule.aircraft.totalCapacity,
        registrationNumber: schedule.aircraft.registrationNumber,
        isActive: schedule.aircraft.isActive,
        createdAt: schedule.aircraft.createdAt,
        updatedAt: schedule.aircraft.updatedAt,
      };
    }

    return cleaned;
  }

  /**
   * Get flight schedules with pagination and filtering
   */
  static async getFlightSchedules(params?: FlightScheduleListParams): Promise<PaginatedResponse<FlightSchedule>> {
    try {
      const searchParams = new URLSearchParams();
      
      if (params?.page !== undefined) searchParams.append("page", params.page.toString());
      if (params?.size !== undefined) searchParams.append("size", params.size.toString());
      if (params?.search) searchParams.append("search", params.search);
      if (params?.date) searchParams.append("date", params.date);
      if (params?.departureAirportId) searchParams.append("departureAirportId", params.departureAirportId.toString());
      if (params?.arrivalAirportId) searchParams.append("arrivalAirportId", params.arrivalAirportId.toString());

      const url = `${this.BASE_PATH}${searchParams.toString() ? `?${searchParams.toString()}` : ""}`;
      const response: ApiResponse<any> = await apiClient.get(url);

      // Clean the schedule data to remove circular references
      const cleanedContent = response.data.content.map((schedule: any) => this.cleanScheduleData(schedule));

      // Transform the backend response to match PaginatedResponse structure
      return {
        content: cleanedContent,
        totalElements: response.data.totalElements,
        totalPages: response.data.totalPages,
        size: response.data.size,
        number: response.data.page,
        first: response.data.first,
        last: response.data.last,
      };
    } catch (error) {
      console.error("Error fetching flight schedules:", error);
      throw error;
    }
  }

  /**
   * Get flight schedule by ID
   */
  static async getFlightScheduleById(scheduleId: string): Promise<FlightSchedule> {
    try {
      const response: ApiResponse<any> = await apiClient.get(`${this.BASE_PATH}/${scheduleId}`);
      return this.cleanScheduleData(response.data);
    } catch (error) {
      console.error("Error fetching flight schedule:", error);
      throw error;
    }
  }

  /**
   * Create a new flight schedule
   */
  static async createFlightSchedule(data: FlightScheduleCreateRequest): Promise<FlightSchedule> {
    try {
      const response: ApiResponse<any> = await apiClient.post(this.BASE_PATH, data);
      return this.cleanScheduleData(response.data);
    } catch (error) {
      console.error("Error creating flight schedule:", error);
      throw error;
    }
  }

  /**
   * Update an existing flight schedule
   */
  static async updateFlightSchedule(scheduleId: string, data: FlightScheduleUpdateRequest): Promise<FlightSchedule> {
    try {
      const response: ApiResponse<any> = await apiClient.put(`${this.BASE_PATH}/${scheduleId}`, data);
      return this.cleanScheduleData(response.data);
    } catch (error) {
      console.error("Error updating flight schedule:", error);
      throw error;
    }
  }

  /**
   * Delete a flight schedule
   */
  static async deleteFlightSchedule(scheduleId: string): Promise<void> {
    try {
      await apiClient.delete(`${this.BASE_PATH}/${scheduleId}`);
    } catch (error) {
      console.error("Error deleting flight schedule:", error);
      throw error;
    }
  }

  /**
   * Get flight schedules for a specific flight
   */
  static async getSchedulesForFlight(flightId: number): Promise<FlightSchedule[]> {
    try {
      const response: ApiResponse<any[]> = await apiClient.get(`${this.BASE_PATH}/flight/${flightId}`);
      return response.data.map((schedule: any) => this.cleanScheduleData(schedule));
    } catch (error) {
      console.error("Error fetching schedules for flight:", error);
      throw error;
    }
  }

  /**
   * Get flight schedule statistics
   */
  static async getFlightScheduleStatistics(): Promise<FlightScheduleStatistics> {
    try {
      const response: ApiResponse<FlightScheduleStatistics> = await apiClient.get(`${this.BASE_PATH}/statistics`);
      return response.data;
    } catch (error) {
      console.error("Error fetching flight schedule statistics:", error);
      throw error;
    }
  }

  /**
   * Format schedule time for display
   */
  static formatScheduleTime(dateTimeString: string): string {
    try {
      const date = new Date(dateTimeString);
      return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZoneName: 'short'
      });
    } catch (error) {
      return dateTimeString;
    }
  }

  /**
   * Format duration from minutes to human readable format
   */
  static formatDuration(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    
    if (hours === 0) {
      return `${remainingMinutes}m`;
    } else if (remainingMinutes === 0) {
      return `${hours}h`;
    } else {
      return `${hours}h ${remainingMinutes}m`;
    }
  }

  /**
   * Get status badge color based on schedule status
   */
  static getStatusBadgeColor(status: string): string {
    switch (status) {
      case 'SCHEDULED':
        return 'bg-blue-100 text-blue-800';
      case 'ACTIVE':
        return 'bg-green-100 text-green-800';
      case 'DELAYED':
        return 'bg-yellow-100 text-yellow-800';
      case 'CANCELLED':
        return 'bg-red-100 text-red-800';
      case 'COMPLETED':
        return 'bg-gray-100 text-gray-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  }

  // Flight Data Generation Methods

  /**
   * Generate daily flight data for a specific date
   */
  static async generateDailyFlightData(targetDate?: string): Promise<any> {
    try {
      const params = new URLSearchParams();
      if (targetDate) {
        params.append("targetDate", targetDate);
      }

      const url = `${this.BASE_PATH}/generate-daily${params.toString() ? `?${params.toString()}` : ""}`;
      const response: ApiResponse<any> = await apiClient.post(url);
      return response.data;
    } catch (error) {
      console.error("Error generating daily flight data:", error);
      throw error;
    }
  }

  /**
   * Generate flight data for a range of dates
   */
  static async generateFlightDataRange(startDate: string, endDate: string): Promise<any> {
    try {
      const params = new URLSearchParams();
      params.append("startDate", startDate);
      params.append("endDate", endDate);

      const url = `${this.BASE_PATH}/generate-range?${params.toString()}`;
      const response: ApiResponse<any> = await apiClient.post(url);
      return response.data;
    } catch (error) {
      console.error("Error generating flight data range:", error);
      throw error;
    }
  }

  /**
   * Generate data for the next N days
   */
  static async generateDataForNextDays(numberOfDays: number = 7): Promise<any> {
    try {
      const params = new URLSearchParams();
      params.append("numberOfDays", numberOfDays.toString());

      const url = `${this.BASE_PATH}/generate-next-days?${params.toString()}`;
      const response: ApiResponse<any> = await apiClient.post(url);
      return response.data;
    } catch (error) {
      console.error("Error generating data for next days:", error);
      throw error;
    }
  }

  /**
   * Clean up old flight data
   */
  static async cleanupOldFlightData(daysToKeep: number = 30): Promise<any> {
    try {
      const params = new URLSearchParams();
      params.append("daysToKeep", daysToKeep.toString());

      const url = `${this.BASE_PATH}/cleanup?${params.toString()}`;
      const response: ApiResponse<any> = await apiClient.delete(url);
      return response.data;
    } catch (error) {
      console.error("Error cleaning up old flight data:", error);
      throw error;
    }
  }
}
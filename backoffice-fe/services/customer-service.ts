import { apiClient } from "@/lib/api-client"
import type { Customer, PaginatedResponse, CustomerStatistics } from "@/types/api"

// Define the actual API types based on backend ViewModels
export interface CustomerAdminVm {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  createdTimestamp: string
}

export interface CustomerListVm {
  totalUser: number
  customers: CustomerAdminVm[]
  totalPage: number
}

export interface CustomerPostVm {
  username: string
  email: string
  firstName: string
  lastName: string
  password: string
  role: string
}

export interface CustomerProfileRequestVm {
  firstName: string
  lastName: string
  email: string
}

export class CustomerService {
  private static readonly BASE_PATH = "/api/customers/backoffice/admin/customers"

  static async getCustomers(params?: {
    page?: number
    size?: number
    search?: string
  }): Promise<PaginatedResponse<Customer>> {
    try {
      const page = params?.page || 0
      const size = params?.size || 10
      
      const response = await apiClient.get<CustomerListVm>(`${this.BASE_PATH}?page=${page}&size=${size}`)
      
      // Transform backend response to frontend format
      const customers: Customer[] = response.customers.map(customer => ({
        id: customer.id,
        name: `${customer.firstName} ${customer.lastName}`,
        email: customer.email,
        phone: "", // Not available in backend response
        tier: "BRONZE" as const, // Default tier
        totalBookings: 0, // Not available in backend response
        totalSpent: 0, // Not available in backend response
        status: "ACTIVE" as const, // Default status
        createdAt: customer.createdTimestamp,
        lastLoginAt: undefined
      }))

      return {
        content: customers,
        totalElements: response.totalUser,
        totalPages: response.totalPage,
        size: size,
        number: page,
        first: page === 0,
        last: page >= response.totalPage - 1
      }
    } catch (error) {
      console.error("Failed to fetch customers:", error)
      throw error
    }
  }

  static async getCustomer(id: string): Promise<Customer> {
    try {
      const response = await apiClient.get<CustomerAdminVm>(`${this.BASE_PATH}/${id}`)
      
      return {
        id: response.id,
        name: `${response.firstName} ${response.lastName}`,
        email: response.email,
        phone: "", // Not available in backend response
        tier: "BRONZE" as const, // Default tier
        totalBookings: 0, // Not available in backend response
        totalSpent: 0, // Not available in backend response
        status: "ACTIVE" as const, // Default status
        createdAt: response.createdTimestamp,
        lastLoginAt: undefined
      }
    } catch (error) {
      console.error("Failed to fetch customer:", error)
      throw error
    }
  }

  static async getCustomerByEmail(email: string): Promise<Customer> {
    try {
      const response = await apiClient.get<CustomerAdminVm>(`${this.BASE_PATH}/search?email=${encodeURIComponent(email)}`)
      
      return {
        id: response.id,
        name: `${response.firstName} ${response.lastName}`,
        email: response.email,
        phone: "", // Not available in backend response
        tier: "BRONZE" as const, // Default tier
        totalBookings: 0, // Not available in backend response
        totalSpent: 0, // Not available in backend response
        status: "ACTIVE" as const, // Default status
        createdAt: response.createdTimestamp,
        lastLoginAt: undefined
      }
    } catch (error) {
      console.error("Failed to fetch customer by email:", error)
      throw error
    }
  }

  static async createCustomer(customer: CustomerPostVm): Promise<Customer> {
    try {
      const response = await apiClient.post<CustomerAdminVm>(`${this.BASE_PATH}`, customer)
      
      return {
        id: response.id,
        name: `${response.firstName} ${response.lastName}`,
        email: response.email,
        phone: "", // Not available in backend response
        tier: "BRONZE" as const, // Default tier
        totalBookings: 0, // Not available in backend response
        totalSpent: 0, // Not available in backend response
        status: "ACTIVE" as const, // Default status
        createdAt: response.createdTimestamp,
        lastLoginAt: undefined
      }
    } catch (error) {
      console.error("Failed to create customer:", error)
      throw error
    }
  }

  static async updateCustomer(id: string, customer: CustomerProfileRequestVm): Promise<void> {
    try {
      await apiClient.put(`${this.BASE_PATH}/${id}`, customer)
    } catch (error) {
      console.error("Failed to update customer:", error)
      throw error
    }
  }

  static async deleteCustomer(id: string): Promise<void> {
    try {
      await apiClient.delete(`${this.BASE_PATH}/${id}`)
    } catch (error) {
      console.error("Failed to delete customer:", error)
      throw error
    }
  }

  static async updateCustomerStatus(id: string, status: Customer["status"]): Promise<Customer> {
    // For now, just return the updated customer since we don't have a specific status endpoint
    const customer = await this.getCustomer(id)
    return { ...customer, status }
  }

  static async updateCustomerTier(id: string, tier: Customer["tier"]): Promise<Customer> {
    // TODO: Replace with real API call
    // return apiClient.put<Customer>(`${this.BASE_PATH}/${id}/tier`, { tier })

    // Mock implementation
    await new Promise((resolve) => setTimeout(resolve, 800))
    const customer = await this.getCustomer(id)
    const updatedCustomer = { ...customer, tier }
    console.log("Mock: Updated customer tier", updatedCustomer)
    return updatedCustomer
  }
  static async getCustomerStatistics(): Promise<CustomerStatistics> {
    try {
      const response = await apiClient.get<CustomerStatistics>(`${this.BASE_PATH}/statistics`)
      return response
    } catch (error) {
      console.error("Failed to fetch customer statistics:", error)
      return {
        totalCustomers: 0,
        activeCustomers: 0,
        newCustomersThisMonth: 0
      }
    }
  }
}



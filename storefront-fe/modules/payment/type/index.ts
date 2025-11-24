// Payment module types
export * from './stripe'

// Base payment types
export interface PaymentMethod {
  id: string
  type: 'card' | 'bank_account' | 'digital_wallet'
  provider: 'stripe' | 'paypal' | 'other'
  last4?: string
  brand?: string
  expiryMonth?: number
  expiryYear?: number
  isDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface PaymentIntent {
  id: string
  status: 'pending' | 'processing' | 'succeeded' | 'failed' | 'canceled'
  amount: number
  currency: string
  description?: string
  clientSecret?: string
  paymentMethodId?: string
  customerId?: string
  transactionId?: string
  createdAt: string
  metadata?: Record<string, string>
}

export interface PaymentTransaction {
  id: string
  paymentIntentId: string
  amount: number
  currency: string
  status: 'pending' | 'processing' | 'succeeded' | 'failed' | 'canceled'
  type: 'payment' | 'refund' | 'partial_refund'
  provider: 'stripe' | 'paypal' | 'other'
  gatewayTransactionId?: string
  gatewayReference?: string
  failureReason?: string
  failureCode?: string
  processedAt?: string
  createdAt: string
  metadata?: Record<string, string>
}

import type { StripeBillingAddress } from './stripe'

export interface CreatePaymentIntentPayload {
  bookingId: string
  amount: number
  currency: string
  customerEmail?: string
  customerName?: string
  description?: string
  sagaId?: string
  paymentMethodType?: string
  metadata?: Record<string, string>
  billingAddress?: StripeBillingAddress
  paymentMethodId?: string
  customerId?: string
  confirmPayment?: boolean
  savePaymentMethod?: boolean
  setAsDefault?: boolean
}

export interface RefundRequest {
  paymentIntentId: string
  transactionId: string
  amount?: number
  reason?: string
  metadata?: Record<string, string>
}

export interface RefundResponse {
  id: string
  amount: number
  currency: string
  status: 'succeeded' | 'pending' | 'failed' | 'canceled'
  reason?: string
  createdAt: string
  paymentIntentId: string
}

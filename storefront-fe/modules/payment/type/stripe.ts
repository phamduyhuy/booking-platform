// Stripe-specific types for frontend integration

export interface StripePaymentIntentRequest {
  bookingId: string
  sagaId?: string
  amount: number
  currency: string
  customerEmail?: string
  customerName?: string
  billingAddress?: StripeBillingAddress
  paymentMethodId?: string
  customerId?: string
  confirmPayment?: boolean
  description?: string
  paymentMethodType?: string
  savePaymentMethod?: boolean
  setAsDefault?: boolean
  metadata?: Record<string, string>
}

export interface StripeBillingAddress {
  line1: string
  line2?: string
  city: string
  state?: string
  postalCode: string
  country: string
}

export interface StripePaymentIntentResponse {
  paymentIntentId: string
  clientSecret: string
  status: StripePaymentStatus
  amount: number
  currency: string
  description?: string
  paymentMethodId?: string
  customerId?: string
  createdAt: string
  error?: StripeError
  transactionId?: string
}

export interface StripeError {
  code: string
  message: string
  type: string
}

export type StripePaymentStatus = 
  | 'requires_payment_method'
  | 'requires_confirmation'
  | 'requires_action'
  | 'processing'
  | 'succeeded'
  | 'canceled'
  | 'payment_failed'

export interface StripePaymentMethod {
  id: string
  type: 'card' | 'bank_account' | 'sepa_debit' | 'ideal' | 'sofort' | 'bancontact'
  card?: StripeCardDetails
  billing_details: StripeBillingDetails
  created: number
  customer?: string
}

export interface StripeCardDetails {
  brand: 'visa' | 'mastercard' | 'amex' | 'discover' | 'jcb' | 'diners' | 'unionpay'
  last4: string
  exp_month: number
  exp_year: number
  funding: 'credit' | 'debit' | 'prepaid' | 'unknown'
  country: string
}

export interface StripeBillingDetails {
  address: StripeBillingAddress
  email?: string
  name?: string
  phone?: string
}

export interface StripeCustomer {
  id: string
  email?: string
  status: 'succeeded' | 'pending' | 'failed' | 'canceled'
  reason?: string
  created: number
  payment_intent: string
}

// Stripe Elements configuration
export interface StripeElementsOptions {
  mode: 'payment' | 'setup' | 'subscription'
  amount?: number
  currency?: string
  paymentMethodTypes?: string[]
  appearance?: StripeAppearance
  locale?: string
  wallets?: {
    applePay?: 'auto' | 'never' | 'always'
    googlePay?: 'auto' | 'never' | 'always'
  }
}

export interface StripeAppearance {
  theme?: 'stripe' | 'night' | 'flat'
  variables?: {
    colorPrimary?: string
    colorBackground?: string
    colorText?: string
    colorDanger?: string
    fontFamily?: string
    spacingUnit?: string
    borderRadius?: string
  }
  rules?: Record<string, any>
}

// Payment form validation
export interface StripePaymentFormData {
  amount: number
  currency: string
  customerEmail: string
  customerName: string
  billingAddress: StripeBillingAddress
  savePaymentMethod?: boolean
  setAsDefault?: boolean
}

// Webhook event types
export interface StripeWebhookEvent {
  id: string
  object: string
  api_version?: string
  created: number
  data: {
    object: any
  }
  livemode: boolean
  pending_webhooks: number
  request?: {
    id: string
    idempotency_key?: string
  }
  type: string
}

// Common Stripe webhook event types
export type StripeWebhookEventType = 
  | 'payment_intent.succeeded'
  | 'payment_intent.payment_failed'
  | 'payment_intent.canceled'
  | 'payment_intent.requires_action'
  | 'charge.dispute.created'
  | 'invoice.payment_succeeded'
  | 'invoice.payment_failed'
  | 'customer.created'
  | 'customer.updated'
  | 'payment_method.attached'
  | 'payment_method.detached'

export interface StripeRefundRequest {
  paymentIntentId: string
  transactionId: string
  amount: number
  reason?: 'duplicate' | 'fraudulent' | 'requested_by_customer'
  metadata?: Record<string, string>
}

export interface StripeRefundResponse {
  id: string
  object: string
  amount: number
  balance_transaction: string
  charge: string
  created: number
  currency: string
  metadata: Record<string, string>
  payment_intent: string
  reason: string | null
  receipt_number: string | null
  source_transfer_reversal: string | null
  status: 'succeeded' | 'pending' | 'failed' | 'canceled'
  transfer_reversal: string | null
}

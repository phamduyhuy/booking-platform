// Payment service integration with backend payment-service
import { 
  CreatePaymentIntentPayload, 
  PaymentIntent, 
  RefundRequest, 
  RefundResponse,
  PaymentMethod,
  PaymentTransaction
} from '../type'
import { stripePaymentService } from './stripe'

// Re-export payment method service
export { paymentMethodService } from './payment-method'
export type { 
  PaymentMethodResponse, 
  AddPaymentMethodRequest 
} from './payment-method'

export const paymentService = {
  /**
   * Create a payment intent
   */
  async createIntent(payload: CreatePaymentIntentPayload): Promise<PaymentIntent> {
    try {
      const response = await stripePaymentService.createPaymentIntent({
        bookingId: payload.bookingId,
        amount: payload.amount,
        currency: payload.currency,
        paymentMethodType: payload.paymentMethodType ?? 'CREDIT_CARD',
        sagaId: payload.sagaId,
        customerEmail: payload.customerEmail,
        customerName: payload.customerName,
        description: payload.description,
        billingAddress: payload.billingAddress,
        paymentMethodId: payload.paymentMethodId,
        customerId: payload.customerId,
        confirmPayment: payload.confirmPayment,
        metadata: payload.metadata,
        savePaymentMethod: payload.savePaymentMethod,
        setAsDefault: payload.setAsDefault,
      })

      return {
        id: response.paymentIntentId,
        status: response.status as PaymentIntent['status'],
        clientSecret: response.clientSecret,
        amount: response.amount,
        currency: response.currency,
        description: response.description,
        paymentMethodId: response.paymentMethodId,
        customerId: response.customerId,
        transactionId: response.transactionId,
        createdAt: response.createdAt,
        metadata: {}
      }
    } catch (error) {
      console.error('Error creating payment intent:', error)
      throw error
    }
  },


  /**
   * Process a payment
   */
  async processPayment(payload: CreatePaymentIntentPayload): Promise<PaymentIntent> {
    try {
      const response = await stripePaymentService.processPayment({
        bookingId: payload.bookingId,
        amount: payload.amount,
        currency: payload.currency,
        customerEmail: payload.customerEmail,
        customerName: payload.customerName,
        description: payload.description
      })

      return {
        id: response.paymentIntentId,
        status: response.status as PaymentIntent['status'],
        clientSecret: response.clientSecret,
        amount: response.amount ?? 0,
        currency: response.currency,
        description: response.description ?? undefined,
        paymentMethodId: response.paymentMethodId,
        customerId: response.customerId,
        createdAt: response.createdAt,
        metadata: {}
      }
    } catch (error) {
      console.error('Error processing payment:', error)
      throw error
    }
  },
  /**
   * Create a refund
   */
  async createRefund(request: RefundRequest): Promise<RefundResponse> {
    try {
      const response = await stripePaymentService.createRefund({
        paymentIntentId: request.paymentIntentId,
        transactionId: request.transactionId,
        amount: request.amount,
        reason: request.reason as any,
        metadata: request.metadata
      })

      return {
        id: response.id,
        amount: response.amount,
        currency: response.currency,
        status: response.status as RefundResponse['status'],
        reason: response.reason,
        createdAt: response.created.toString(),
        paymentIntentId: response.payment_intent
      }
    } catch (error) {
      console.error('Error creating refund:', error)
      throw error
    }
  },

  /**
   * Get payment status
   */
  async getPaymentStatus(transactionId: string): Promise<PaymentIntent> {
    try {
      const response = await stripePaymentService.getPaymentStatus(transactionId)
      
      return {
        id: response.paymentIntentId,
        status: response.status as PaymentIntent['status'],
        clientSecret: response.clientSecret,
        amount: response.amount,
        currency: response.currency,
        description: response.description,
        paymentMethodId: response.paymentMethodId,
        customerId: response.customerId,
        createdAt: response.createdAt,
        metadata: {}
      }
    } catch (error) {
      console.error('Error getting payment status:', error)
      throw error
    }
  },

  /**
   * Cancel a payment
   */
  async cancelPayment(transactionId: string): Promise<void> {
    try {
      await stripePaymentService.cancelPayment(transactionId)
    } catch (error) {
      console.error('Error canceling payment:', error)
      throw error
    }
  },

  /**
   * Get user's payment methods
   */
  async getPaymentMethods(): Promise<PaymentMethod[]> {
    try {
      const stripeMethods = await stripePaymentService.getPaymentMethods()
      
      return stripeMethods.map(method => ({
        id: method.id,
        type: method.type as PaymentMethod['type'],
        provider: 'stripe' as const,
        last4: method.card?.last4,
        brand: method.card?.brand,
        expiryMonth: method.card?.exp_month,
        expiryYear: method.card?.exp_year,
        isDefault: false, // This would need to be determined from backend
        isActive: true,
        createdAt: new Date(method.created * 1000).toISOString(),
        updatedAt: new Date(method.created * 1000).toISOString()
      }))
    } catch (error) {
      console.error('Error getting payment methods:', error)
      throw error
    }
  },
  /**
   * Get payment summary by booking ID
   */
  async getPaymentSummary(bookingId: string): Promise<any> {
    try {
      return await stripePaymentService.getPaymentSummary(bookingId)
    } catch (error) {
      console.error('Error getting payment summary:', error)
      throw error
    }
  }
}

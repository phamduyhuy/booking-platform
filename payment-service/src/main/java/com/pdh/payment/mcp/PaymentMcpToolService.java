package com.pdh.payment.mcp;

import com.pdh.payment.model.Payment;
import com.pdh.payment.model.PaymentMethod;
import com.pdh.payment.model.PaymentTransaction;
import com.pdh.payment.model.enums.PaymentMethodType;
import com.pdh.payment.model.enums.PaymentProvider;
import com.pdh.payment.model.enums.PaymentStatus;
import com.pdh.payment.repository.PaymentMethodRepository;
import com.pdh.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Payment MCP Tool Service
 *
 * Exposes AI tools for retrieving stored payment methods and executing Stripe
 * payments via the internal payment service. All successful or failed payment
 * attempts are persisted in BookingSmart for reconciliation and booking flows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentMcpToolService {

    private final PaymentService paymentService;
    private final PaymentMethodRepository paymentMethodRepository;

    private static final String STRIPE_PAYMENT_METHOD_PREFIX = "pm_";
    private static final String STRIPE_CUSTOMER_ID_PREFIX = "customerId=";
    private static final String STRIPE_CUSTOMER_ALT_PREFIX = "stripeCustomerId=";

    /**
     * Get payment methods stored in our database with Stripe payment method IDs
     * AI Agent uses these IDs with the payment MCP tools for secure charging
     */
    @McpTool(name = "get_user_stored_payment_methods", description = "Get payment methods stored in BookingSmart database. Returns Stripe payment method IDs "
            +
            "(pm_xxx) that are required by the payment MCP `process_payment` tool. " +
            "Each method includes: methodId (our DB ID), stripePaymentMethodId (use for charging), " +
            "stripeCustomerId (if available), displayName, card details, and isDefault flag. " +
            "IMPORTANT: Use the stripePaymentMethodId with process_payment, not our methodId.")
    public Map<String, Object> getUserStoredPaymentMethods(
            @McpToolParam(description = "User ID to get payment methods for (UUID format)") String userId) {
        try {
            log.info("AI Tool: Getting payment methods for user={}", userId);

            UUID userUuid = UUID.fromString(userId);
            List<PaymentMethod> paymentMethods = paymentMethodRepository
                    .findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtDesc(userUuid);

            if (paymentMethods.isEmpty()) {
                return Map.of(
                        "success", true,
                        "paymentMethods", Collections.emptyList(),
                        "message", "No payment methods found. User needs to add a payment method first.",
                        "suggestion", "User should add a credit card or other payment method before making a booking.");
            }

            List<Map<String, Object>> methods = paymentMethods.stream()
                    .map(pm -> {
                        Map<String, Object> methodMap = new LinkedHashMap<>();
                        methodMap.put("methodId", pm.getMethodId().toString());
                        methodMap.put("displayName", pm.getDisplayName());
                        methodMap.put("methodType", pm.getMethodType().toString());
                        methodMap.put("provider", pm.getProvider().toString());
                        methodMap.put("isDefault", pm.getIsDefault());

                        // Card details if available
                        if (pm.getCardLastFour() != null) {
                            methodMap.put("cardLastFour", pm.getCardLastFour());
                        }
                        if (pm.getCardBrand() != null) {
                            methodMap.put("cardBrand", pm.getCardBrand());
                        }
                        if (pm.getCardExpiryMonth() != null && pm.getCardExpiryYear() != null) {
                            methodMap.put("cardExpiry", String.format("%02d/%d",
                                    pm.getCardExpiryMonth(), pm.getCardExpiryYear()));
                        }

                        // Extract Stripe data from provider metadata
                        StripeProviderDetails stripeDetails = extractStripeProviderDetails(pm);
                        if (stripeDetails.hasPaymentMethodId()) {
                            methodMap.put("stripePaymentMethodId", stripeDetails.getPaymentMethodId());
                        }
                        if (stripeDetails.hasCustomerId()) {
                            methodMap.put("stripeCustomerId", stripeDetails.getCustomerId());
                        }

                        // Bank details if available
                        if (pm.getBankName() != null) {
                            methodMap.put("bankName", pm.getBankName());
                        }
                        if (pm.getBankAccountLastFour() != null) {
                            methodMap.put("accountLastFour", pm.getBankAccountLastFour());
                        }

                        return methodMap;
                    })
                    .collect(Collectors.toList());

            // Find default method
            Optional<PaymentMethod> defaultMethod = paymentMethods.stream()
                    .filter(PaymentMethod::getIsDefault)
                    .findFirst();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("paymentMethods", methods);
            response.put("totalMethods", methods.size());

            if (defaultMethod.isPresent()) {
                response.put("defaultMethodId", defaultMethod.get().getMethodId().toString());
                response.put("suggestion", "User has a default payment method. You can use it for quick checkout.");
            } else {
                response.put("suggestion", "User should select a payment method to proceed with payment.");
            }

            return response;

        } catch (Exception e) {
            log.error("AI Tool: Error getting payment methods", e);
            return createErrorResponse("Failed to get payment methods: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPaymentResponse(PaymentTransaction transaction, Payment payment,
            PaymentMethod paymentMethod) {
        Map<String, Object> response = new LinkedHashMap<>();

        PaymentStatus status = transaction.getStatus();
        boolean success = status != null && status.isSuccessful();
        boolean processing = status != null && status.isInProgress();

        response.put("success", success);
        response.put("processing", processing);
        response.put("bookingId", payment.getBookingId().toString());
        response.put("transactionId", transaction.getTransactionId().toString());
        response.put("paymentReference", payment.getPaymentReference());
        response.put("status", status != null ? status.toString() : PaymentStatus.ERROR.toString());
        response.put("statusDescription", status != null ? status.getDescription() : "Unknown payment status");
        response.put("amount", transaction.getAmount());
        response.put("currency", transaction.getCurrency());
        response.put("provider", transaction.getProvider().toString());

        if (transaction.getGatewayTransactionId() != null) {
            response.put("stripePaymentIntentId", transaction.getGatewayTransactionId());
        }
        if (transaction.getGatewayStatus() != null) {
            response.put("stripeStatus", transaction.getGatewayStatus());
        }

        Map<String, Object> methodInfo = new LinkedHashMap<>();
        methodInfo.put("type", paymentMethod.getMethodType().toString());
        methodInfo.put("displayName", paymentMethod.getDisplayName());
        if (paymentMethod.getCardBrand() != null) {
            methodInfo.put("brand", paymentMethod.getCardBrand());
        }
        if (paymentMethod.getCardLastFour() != null) {
            methodInfo.put("lastFour", paymentMethod.getCardLastFour());
        }
        methodInfo.put("isDefault", paymentMethod.getIsDefault());
        response.put("paymentMethod", methodInfo);

        if (transaction.getFailureReason() != null) {
            response.put("failureReason", transaction.getFailureReason());
        }
        if (transaction.getFailureCode() != null) {
            response.put("failureCode", transaction.getFailureCode());
        }

        String message;
        String nextStep;

        if (success) {
            message = "Payment processed successfully.";
            nextStep = "Payment complete. Proceed to confirm booking status with the user.";
        } else if (processing) {
            message = "Payment is processing with Stripe. Please re-check payment status shortly.";
            nextStep = "Use get_booking_payment_status to monitor this booking and inform the user once it completes.";
        } else {
            message = "Payment failed. " + (transaction.getFailureReason() != null
                    ? transaction.getFailureReason()
                    : "Please select a different payment method or retry.");
            nextStep = "Ask the user to confirm another payment method or retry the payment.";
        }

        response.put("message", message);
        response.put("nextStep", nextStep);

        return response;
    }

    private StripeProviderDetails extractStripeProviderDetails(PaymentMethod paymentMethod) {
        String rawProviderData = paymentMethod.getProviderData() != null
                ? paymentMethod.getProviderData().trim()
                : "";
        String paymentMethodId = null;
        String customerId = null;

        if (paymentMethod.getToken() != null
                && paymentMethod.getToken().trim().startsWith(STRIPE_PAYMENT_METHOD_PREFIX)) {
            paymentMethodId = paymentMethod.getToken().trim();
        }

        if (!rawProviderData.isEmpty()) {
            String[] segments = rawProviderData.split("[;,]");
            for (String segment : segments) {
                String value = segment.trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (value.startsWith(STRIPE_PAYMENT_METHOD_PREFIX)) {
                    paymentMethodId = value;
                } else if (value.startsWith(STRIPE_CUSTOMER_ID_PREFIX)) {
                    customerId = value.substring(STRIPE_CUSTOMER_ID_PREFIX.length()).trim();
                } else if (value.startsWith(STRIPE_CUSTOMER_ALT_PREFIX)) {
                    customerId = value.substring(STRIPE_CUSTOMER_ALT_PREFIX.length()).trim();
                }
            }

            if (paymentMethodId == null && rawProviderData.startsWith(STRIPE_PAYMENT_METHOD_PREFIX)) {
                paymentMethodId = rawProviderData;
            }
        }

        return new StripeProviderDetails(paymentMethodId, customerId);
    }

    private static final class StripeProviderDetails {
        private final String paymentMethodId;
        private final String customerId;

        private StripeProviderDetails(String paymentMethodId, String customerId) {
            this.paymentMethodId = paymentMethodId;
            this.customerId = customerId;
        }

        private boolean hasPaymentMethodId() {
            return paymentMethodId != null && !paymentMethodId.isBlank();
        }

        private boolean hasCustomerId() {
            return customerId != null && !customerId.isBlank();
        }

        private String getPaymentMethodId() {
            return paymentMethodId;
        }

        private String getCustomerId() {
            return customerId;
        }
    }

    /**
     * Process payment via Stripe and record the outcome.
     */
    @McpTool(generateOutputSchema = true, name = "process_payment", description = "Process a Stripe payment using a stored payment method in BookingSmart. "
            +
            "Requires bookingId, userId, amount, currency, and paymentMethodId. " +
            "IMPORTANT: You SHOULD pass the sagaId that was returned from the create_booking tool to maintain saga correlation. "
            +
            "If sagaId is not provided, one will be auto-generated, but this breaks the booking-payment correlation. " +
            "Automatically creates the Stripe PaymentIntent, confirms it off-session, and records " +
            "the resulting transaction for the booking. Returns transaction details and next steps.")
    public Map<String, Object> processPayment(
            @McpToolParam(description = "Booking ID to process payment for (UUID format)") String bookingId,

            @McpToolParam(description = "User ID making the payment (UUID format)") String userId,

            @McpToolParam(description = "Payment amount (decimal number)") BigDecimal amount,

            @McpToolParam(description = "Currency code (e.g., 'USD', 'VND', 'EUR')") String currency,

            @McpToolParam(description = "Payment method ID to use (UUID format). Get from get_user_stored_payment_methods tool.") String paymentMethodId,

            @McpToolParam(description = "Description or reference for the payment") String description,

            @McpToolParam(description = "Saga ID from booking creation for correlation") String sagaId) {
        return executeStripePayment(bookingId, userId, amount, currency, paymentMethodId, description, sagaId,
                "process_payment");
    }

    /**
     * Legacy alias retained for backward compatibility. Prefer using
     * process_payment.
     */
    @McpTool(generateOutputSchema = true, name = "record_successful_payment", description = "Legacy alias for process_payment. Performs the same Stripe charge and recording flow. Prefer using process_payment.")
    public Map<String, Object> recordSuccessfulPayment(
            @McpToolParam(description = "Booking ID to process payment for (UUID format)") String bookingId,

            @McpToolParam(description = "User ID making the payment (UUID format)") String userId,

            @McpToolParam(description = "Payment amount (decimal number)") BigDecimal amount,

            @McpToolParam(description = "Currency code (e.g., 'USD', 'VND', 'EUR')") String currency,

            @McpToolParam(description = "Payment method ID to use (UUID format). Get from get_user_stored_payment_methods tool.") String paymentMethodId,

            @McpToolParam(description = "Description or reference for the payment") String description,

            @McpToolParam(description = "Saga ID from booking creation for correlation") String sagaId) {
        return executeStripePayment(bookingId, userId, amount, currency, paymentMethodId, description, sagaId,
                "record_successful_payment");
    }

    private Map<String, Object> executeStripePayment(
            String bookingId,
            String userId,
            BigDecimal amount,
            String currency,
            String paymentMethodId,
            String description,
            String sagaId,
            String toolName) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return createErrorResponse("Amount must be greater than zero.");
            }

            if (currency == null || currency.trim().isEmpty()) {
                return createErrorResponse("Currency is required.");
            }

            UUID bookingUuid = UUID.fromString(bookingId);
            UUID userUuid = UUID.fromString(userId);
            UUID methodUuid = UUID.fromString(paymentMethodId);

            Optional<PaymentMethod> methodOpt = paymentMethodRepository.findById(methodUuid);
            if (methodOpt.isEmpty()) {
                return createErrorResponse(
                        "Payment method not found. Please use get_user_stored_payment_methods to select a valid payment method.");
            }

            PaymentMethod paymentMethod = methodOpt.get();

            if (!paymentMethod.getUserId().equals(userUuid)) {
                return createErrorResponse("Payment method does not belong to this user.");
            }

            if (!Boolean.TRUE.equals(paymentMethod.getIsActive())) {
                return createErrorResponse("Payment method is not active. Please select another payment method.");
            }

            if (paymentMethod.getProvider() != PaymentProvider.STRIPE) {
                return createErrorResponse(
                        "Payment method provider is not Stripe. Choose a Stripe-backed payment method.");
            }

            StripeProviderDetails stripeDetails = extractStripeProviderDetails(paymentMethod);
            if (!stripeDetails.hasPaymentMethodId()) {
                return createErrorResponse(
                        "Stored payment method is missing a Stripe payment method ID. Ask the user to re-link their card.");
            }

            paymentMethod.setToken(stripeDetails.getPaymentMethodId());

            String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);

            Payment payment = new Payment();
            payment.setPaymentReference(Payment.generatePaymentReference());
            payment.setBookingId(bookingUuid);
            payment.setUserId(userUuid);
            payment.setAmount(amount);
            payment.setCurrency(normalizedCurrency);
            payment.setDescription(description != null ? description : "Payment for booking " + bookingId);
            payment.setMethodType(paymentMethod.getMethodType());
            payment.setProvider(PaymentProvider.STRIPE);
            if (sagaId != null && !sagaId.isBlank()) {
                payment.setSagaId(sagaId);
            }

            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("paymentMethodId", paymentMethodId);
            additionalData.put("offSession", true);
            additionalData.put("initiatedBy", "mcp_server");
            additionalData.put("toolName", toolName);
            additionalData.put("stripePaymentMethodId", stripeDetails.getPaymentMethodId());
            if (stripeDetails.hasCustomerId()) {
                additionalData.put("stripeCustomerId", stripeDetails.getCustomerId());
            }

            log.info("AI Tool [{}]: Executing Stripe payment - bookingId={}, amount={} {}, paymentMethodId={}",
                    toolName, bookingId, amount, normalizedCurrency, paymentMethodId);

            PaymentTransaction transaction = paymentService.processPayment(payment, paymentMethod, additionalData);

            return buildPaymentResponse(transaction, payment, paymentMethod);

        } catch (IllegalArgumentException invalidId) {
            log.error("AI Tool [{}]: Invalid identifier while processing payment", toolName, invalidId);
            return createErrorResponse("Invalid identifier: " + invalidId.getMessage());
        } catch (Exception e) {
            log.error("AI Tool [{}]: Error processing payment", toolName, e);
            return createErrorResponse("Failed to process payment: " + e.getMessage());
        }
    }

    /**
     * Get payment status and history for a booking
     */
    @McpTool(generateOutputSchema = true, name = "get_booking_payment_status", description = "Get payment status and transaction history for a booking. "
            +
            "Returns all payment attempts, transaction statuses, amounts, and payment methods used. " +
            "Useful for checking if payment was successful or troubleshooting payment issues.")
    public Map<String, Object> getBookingPaymentStatus(
            @McpToolParam(description = "Booking ID to check payment status for (UUID format)") String bookingId,

            @McpToolParam(description = "User ID who owns the booking (UUID format)") String userId) {
        try {
            log.info("AI Tool: Getting payment status for bookingId={}", bookingId);

            UUID bookingUuid = UUID.fromString(bookingId);
            UUID userUuid = UUID.fromString(userId);

            // Get payment for booking
            Optional<Payment> paymentOpt = paymentService.getPaymentByBookingId(bookingUuid);

            if (paymentOpt.isEmpty()) {
                return Map.of(
                        "success", true,
                        "hasPayment", false,
                        "message", "No payment found for this booking yet. Payment may still be pending.");
            }

            Payment payment = paymentOpt.get();

            // Verify payment belongs to user
            if (!payment.getUserId().equals(userUuid)) {
                return createErrorResponse("Payment does not belong to this user.");
            }

            // Get payment transactions
            List<PaymentTransaction> transactions = paymentService.getPaymentTransactions(payment.getPaymentId());

            List<Map<String, Object>> transactionList = transactions.stream()
                    .map(txn -> {
                        Map<String, Object> txnMap = new LinkedHashMap<>();
                        txnMap.put("transactionId", txn.getTransactionId().toString());
                        txnMap.put("status", txn.getStatus().toString());
                        txnMap.put("amount", txn.getAmount());
                        txnMap.put("currency", txn.getCurrency());
                        txnMap.put("createdAt", txn.getCreatedAt());
                        if (txn.getFailureReason() != null) {
                            txnMap.put("failureReason", txn.getFailureReason());
                        }
                        return txnMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("hasPayment", true);
            response.put("paymentId", payment.getPaymentId().toString());
            response.put("paymentReference", payment.getPaymentReference());
            response.put("status", payment.getStatus().toString());
            response.put("totalAmount", payment.getAmount());
            response.put("currency", payment.getCurrency());
            response.put("methodType", payment.getMethodType().toString());
            response.put("provider", payment.getProvider().toString());
            response.put("transactions", transactionList);
            response.put("totalTransactions", transactionList.size());

            // Add status-specific message
            switch (payment.getStatus()) {
                case COMPLETED:
                case CONFIRMED:
                    response.put("message", "Payment completed successfully.");
                    break;
                case PENDING:
                case PROCESSING:
                    response.put("message", "Payment is pending confirmation.");
                    break;
                case FAILED:
                case DECLINED:
                case ERROR:
                    response.put("message", "Payment failed. Please try again with a different payment method.");
                    break;
                case CANCELLED:
                    response.put("message", "Payment was cancelled.");
                    break;
                case REFUND_COMPLETED:
                    response.put("message", "Payment has been refunded.");
                    break;
                case REFUND_PENDING:
                case REFUND_PROCESSING:
                case REFUND_FAILED:
                    response.put("message", "Refund is being processed.");
                    break;
                case COMPENSATION_PENDING:
                case COMPENSATION_COMPLETED:
                case COMPENSATION_FAILED:
                    response.put("message", "Payment compensation in progress.");
                    break;
                case TIMEOUT:
                    response.put("message", "Payment timed out. Please try again.");
                    break;
            }

            return response;

        } catch (Exception e) {
            log.error("AI Tool: Error getting payment status", e);
            return createErrorResponse("Failed to get payment status: " + e.getMessage());
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
                "success", false,
                "error", message);
    }
}

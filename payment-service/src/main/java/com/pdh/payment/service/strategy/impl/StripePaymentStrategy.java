package com.pdh.payment.service.strategy.impl;

import com.pdh.payment.config.StripeConfig;
import com.pdh.payment.model.Payment;
import com.pdh.payment.model.PaymentMethod;
import com.pdh.payment.model.PaymentTransaction;
import com.pdh.payment.model.enums.PaymentProvider;
import com.pdh.payment.model.enums.PaymentStatus;
import com.pdh.payment.model.enums.PaymentTransactionType;
import com.pdh.payment.model.enums.PaymentMethodType;
import com.pdh.payment.service.strategy.PaymentStrategy;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.PaymentIntentUpdateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stripe Payment Strategy Implementation
 * Handles Stripe payment processing with full SDK integration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripePaymentStrategy implements PaymentStrategy {

    private final StripeConfig stripeConfig;

    private static final String STRATEGY_NAME = "Stripe Payment Strategy";
    private static final BigDecimal STRIPE_FEE_RATE = new BigDecimal("0.029"); // 2.9%
    private static final BigDecimal STRIPE_FIXED_FEE = new BigDecimal("0.30"); // $0.30
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg",
            "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

    @jakarta.annotation.PostConstruct
    public void initializeStripe() {
        if (stripeConfig.isValid()) {
            Stripe.apiKey = stripeConfig.getApi().getSecretKey();
            log.info("Stripe API initialized with key: {}***",
                    stripeConfig.getApi().getSecretKey().substring(0, 8));
        } else {
            log.warn("Stripe configuration is invalid, strategy will not be available");
        }
    }

    @Override
    public PaymentTransaction processPayment(Payment payment, PaymentMethod paymentMethod,
            Map<String, Object> additionalData) {
        log.info("Processing Stripe payment for payment ID: {} with method: {}",
                payment.getPaymentId(), paymentMethod.getMethodType());

        PaymentTransaction transaction = createBaseTransaction(payment, paymentMethod);

        try {
            PaymentIntent paymentIntent;

            // Check if this is an off-session payment with stored payment method
            boolean isOffSession = additionalData != null &&
                    Boolean.TRUE.equals(additionalData.get("offSession"));

            String storedPaymentMethodId = resolvePaymentMethodId(paymentMethod, additionalData);
            boolean hasStoredPaymentMethod = storedPaymentMethodId != null && !storedPaymentMethodId.isEmpty();

            if (isOffSession || hasStoredPaymentMethod) {
                // Server-side off-session payment with stored payment method
                log.info("Processing off-session payment with stored payment method for payment: {}",
                        payment.getPaymentId());
                paymentIntent = createAndConfirmPaymentIntent(payment, paymentMethod, additionalData);
            } else {
                // Traditional payment intent creation (for manual confirmation flows)
                log.info("Processing traditional payment intent for payment: {}", payment.getPaymentId());
                paymentIntent = createPaymentIntentWithRetry(payment, paymentMethod, additionalData);
            }

            // Update transaction with Stripe data
            updateTransactionWithStripeData(transaction, paymentIntent);

            log.info("Stripe payment intent created successfully: {} with status: {}",
                    paymentIntent.getId(), paymentIntent.getStatus());

        } catch (StripeException e) {
            log.error("Stripe payment failed for payment ID: {} - {}", payment.getPaymentId(), e.getMessage());
            handleStripeError(transaction, e);
        } catch (Exception e) {
            log.error("Unexpected error during Stripe payment processing", e);
            transaction.markAsFailed("Unexpected error: " + e.getMessage(), "INTERNAL_ERROR");
        }

        return transaction;
    }

    @Override
    public PaymentTransaction processRefund(PaymentTransaction originalTransaction, BigDecimal refundAmount,
            String reason) {
        log.info("Processing Stripe refund for transaction: {} with amount: {}",
                originalTransaction.getTransactionId(), refundAmount);

        PaymentTransaction refundTransaction = createRefundTransaction(originalTransaction, refundAmount, reason);

        try {
            // Create Stripe Refund with simple retry
            Refund refund = createStripeRefundWithRetry(originalTransaction, refundAmount, reason);

            // Update transaction with refund data
            updateRefundTransactionWithStripeData(refundTransaction, refund);

            log.info("Stripe refund created successfully: {}", refund.getId());

        } catch (StripeException e) {
            log.error("Stripe refund failed for transaction: {} - {}", originalTransaction.getTransactionId(),
                    e.getMessage());
            handleStripeRefundError(refundTransaction, e);
        } catch (Exception e) {
            log.error("Unexpected error during Stripe refund processing", e);
            refundTransaction.markAsFailed("Unexpected error: " + e.getMessage(), "INTERNAL_ERROR");
        }

        return refundTransaction;
    }

    @Override
    public PaymentTransaction verifyPaymentStatus(PaymentTransaction transaction) {
        log.debug("Verifying Stripe payment status for transaction: {}", transaction.getTransactionId());

        if (transaction.getGatewayTransactionId() == null) {
            log.warn("No Stripe payment intent ID found for transaction: {}", transaction.getTransactionId());
            return transaction;
        }

        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(
                    transaction.getGatewayTransactionId(),
                    PaymentIntentRetrieveParams.builder().build(),
                    null);

            updateTransactionStatusFromStripe(transaction, paymentIntent);

        } catch (StripeException e) {
            log.error("Failed to verify Stripe payment status for transaction: {}",
                    transaction.getTransactionId(), e);
        }

        return transaction;
    }

    @Override
    public PaymentTransaction cancelPayment(PaymentTransaction transaction, String reason) {
        log.info("Cancelling Stripe payment for transaction: {} with reason: {}",
                transaction.getTransactionId(), reason);

        try {
            if (transaction.getGatewayTransactionId() != null) {
                PaymentIntent paymentIntent = PaymentIntent.retrieve(transaction.getGatewayTransactionId());

                if ("requires_payment_method".equals(paymentIntent.getStatus()) ||
                        "requires_confirmation".equals(paymentIntent.getStatus())) {
                    // Cancel the payment intent
                    paymentIntent.cancel();
                    log.info("Stripe payment intent cancelled: {}", paymentIntent.getId());
                }
            }

            transaction.setStatus(PaymentStatus.CANCELLED);
            transaction.setGatewayStatus("CANCELLED");
            transaction.setFailureReason(reason);
            transaction.setFailureCode("USER_CANCELLED");
            transaction.setProcessedAt(ZonedDateTime.now());

        } catch (StripeException e) {
            log.error("Failed to cancel Stripe payment intent", e);
            // Still mark as cancelled locally even if Stripe call fails
            transaction.setStatus(PaymentStatus.CANCELLED);
            transaction.setFailureReason(reason + " (Stripe cancellation failed: " + e.getMessage() + ")");
        }

        return transaction;
    }

    @Override
    public boolean supports(PaymentMethod paymentMethod) {
        return paymentMethod.getProvider() == PaymentProvider.STRIPE && stripeConfig.isValid();
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public ValidationResult validatePaymentMethod(PaymentMethod paymentMethod) {
        if (!supports(paymentMethod)) {
            return ValidationResult.failure("Payment method not supported by Stripe strategy", "UNSUPPORTED_METHOD");
        }

        if (!paymentMethod.getIsActive()) {
            return ValidationResult.failure("Payment method is not active", "INACTIVE_METHOD");
        }

        if (paymentMethod.getToken() == null || paymentMethod.getToken().trim().isEmpty()) {
            return ValidationResult.failure("Stripe payment method token is required", "MISSING_TOKEN");
        }

        return ValidationResult.success();
    }

    @Override
    public BigDecimal getProcessingFee(BigDecimal amount, PaymentMethod paymentMethod) {
        // Stripe fee: 2.9% + $0.30
        BigDecimal percentageFee = amount.multiply(STRIPE_FEE_RATE);
        return percentageFee.add(STRIPE_FIXED_FEE);
    }

    @Override
    public boolean supportsRefunds() {
        return true;
    }

    @Override
    public boolean supportsPartialRefunds() {
        return true;
    }

    @Override
    public int getMaxRefundWindowDays() {
        return 120; // Stripe allows refunds up to 120 days
    }

    // Helper methods

    private PaymentTransaction createBaseTransaction(Payment payment, PaymentMethod paymentMethod) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setTransactionReference(
                PaymentTransaction.generateTransactionReference(PaymentTransactionType.PAYMENT));
        transaction.setTransactionType(PaymentTransactionType.PAYMENT);
        transaction.setStatus(PaymentStatus.PROCESSING);
        transaction.setAmount(payment.getAmount());
        transaction.setCurrency(payment.getCurrency());
        transaction.setDescription("Stripe payment for " + payment.getDescription());
        transaction.setProvider(PaymentProvider.STRIPE);
        transaction.setSagaId(payment.getSagaId());
        transaction.setSagaStep("STRIPE_PAYMENT_PROCESSING");

        // Calculate and set gateway fee
        BigDecimal gatewayFee = getProcessingFee(payment.getAmount(), paymentMethod);
        transaction.setGatewayFee(gatewayFee);

        return transaction;
    }

    private PaymentIntent createPaymentIntent(Payment payment, PaymentMethod paymentMethod,
            Map<String, Object> additionalData) throws StripeException {

        String currency = payment.getCurrency() != null
                ? payment.getCurrency().toLowerCase()
                : stripeConfig.getSettings().getCurrency();

        long amountInMinorUnits = toStripeAmount(payment.getAmount(), currency);

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(currency)
                .setPaymentMethod(paymentMethod.getToken())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.valueOf(
                        stripeConfig.getSettings().getCaptureMethod().toUpperCase()))
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.valueOf(
                        stripeConfig.getSettings().getConfirmationMethod().toUpperCase()))
                .setStatementDescriptorSuffix(stripeConfig.getSettings().getStatementDescriptor());

        // Add metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", payment.getPaymentId().toString());
        metadata.put("booking_id", payment.getBookingId().toString());
        metadata.put("user_id", payment.getUserId().toString());
        if (payment.getSagaId() != null) {
            metadata.put("saga_id", payment.getSagaId());
        }
        paramsBuilder.putAllMetadata(metadata);

        // Add customer email if available
        if (additionalData != null && additionalData.containsKey("customer_email")) {
            paramsBuilder.setReceiptEmail((String) additionalData.get("customer_email"));
        }

        return PaymentIntent.create(paramsBuilder.build());
    }

    /**
     * Create and confirm a PaymentIntent for server-side processing with stored
     * payment method
     * This is used for MCP/API-initiated payments where the user is not present
     * (off_session)
     */
    public PaymentIntent createAndConfirmPaymentIntent(Payment payment, PaymentMethod paymentMethod,
            Map<String, Object> additionalData) throws StripeException {
        String currency = payment.getCurrency() != null
                ? payment.getCurrency().toLowerCase()
                : stripeConfig.getSettings().getCurrency();

        long amountInMinorUnits = toStripeAmount(payment.getAmount(), currency);

        // Get stored Stripe payment method ID
        String stripePaymentMethodId = resolvePaymentMethodId(paymentMethod, additionalData);
        if (stripePaymentMethodId == null || stripePaymentMethodId.isEmpty()) {
            throw new IllegalStateException("Payment method does not have Stripe payment method ID stored");
        }

        // Build payment intent params for off-session charge
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(currency)
                .setPaymentMethod(stripePaymentMethodId) // Use stored payment method ID
                .setConfirm(true) // Auto-confirm the payment
                .setOffSession(true) // Indicate this is an off-session payment (no user present)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC) // Capture immediately
                .setStatementDescriptorSuffix(stripeConfig.getSettings().getStatementDescriptor())
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC);

        // Add metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", payment.getPaymentId().toString());
        metadata.put("booking_id", payment.getBookingId().toString());
        metadata.put("user_id", payment.getUserId().toString());
        metadata.put("payment_method_id", paymentMethod.getMethodId().toString());
        metadata.put("processing_type", "off_session");
        metadata.put("initiated_by", "mcp_server");
        if (payment.getSagaId() != null) {
            metadata.put("saga_id", payment.getSagaId());
        }
        paramsBuilder.putAllMetadata(metadata);

        // Add customer email if available
        if (additionalData != null && additionalData.containsKey("customer_email")) {
            paramsBuilder.setReceiptEmail((String) additionalData.get("customer_email"));
        }

        String customerId = resolveCustomerId(paymentMethod, additionalData);
        if (customerId != null) {
            paramsBuilder.setCustomer(customerId);
        }

        log.info("Creating off-session payment intent for payment {} with stored payment method {}",
                payment.getPaymentId(), paymentMethod.getMethodId());

        try {
            // Create and confirm the payment intent
            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            log.info("Off-session payment intent created and confirmed: {} with status: {}",
                    paymentIntent.getId(), paymentIntent.getStatus());

            return paymentIntent;

        } catch (StripeException e) {
            // Handle specific off-session payment errors
            if ("authentication_required".equals(e.getCode())) {
                log.error(
                        "3D Secure authentication required for off-session payment. Payment method may need re-authentication.");
                throw new IllegalStateException(
                        "Payment requires authentication. User needs to re-authenticate their payment method.", e);
            }
            throw e;
        }
    }

    /**
     * Resolve the Stripe customer ID using additional tool data or stored provider
     * metadata.
     */
    private String resolveCustomerId(PaymentMethod paymentMethod, Map<String, Object> additionalData) {
        if (additionalData != null) {
            Object provided = additionalData.get("stripeCustomerId");
            if (provided instanceof String providedCustomer) {
                String trimmed = providedCustomer.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return extractCustomerId(paymentMethod.getProviderData());
    }

    /**
     * Resolve the Stripe payment method ID using tool overrides, tokens, or
     * provider metadata.
     */
    private String resolvePaymentMethodId(PaymentMethod paymentMethod, Map<String, Object> additionalData) {
        if (additionalData != null) {
            Object provided = additionalData.get("stripePaymentMethodId");
            if (provided instanceof String providedId) {
                String sanitized = sanitizePaymentMethodId(providedId);
                if (sanitized != null) {
                    return sanitized;
                }
            }
        }

        if (paymentMethod.getToken() != null) {
            String sanitizedToken = sanitizePaymentMethodId(paymentMethod.getToken());
            if (sanitizedToken != null) {
                return sanitizedToken;
            }
        }

        if (paymentMethod.getProviderData() != null) {
            String sanitizedProviderData = sanitizePaymentMethodId(paymentMethod.getProviderData());
            if (sanitizedProviderData != null) {
                return sanitizedProviderData;
            }
        }

        return null;
    }

    private String sanitizePaymentMethodId(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("pm_")) {
            int separatorIndex = findSeparatorIndex(trimmed);
            return separatorIndex > 0 ? trimmed.substring(0, separatorIndex) : trimmed;
        }

        String[] segments = trimmed.split("[;,\\s]");
        for (String segment : segments) {
            String candidate = segment.trim();
            if (candidate.startsWith("pm_")) {
                return candidate;
            }
        }

        return null;
    }

    private int findSeparatorIndex(String value) {
        int end = value.length();
        int semicolon = value.indexOf(';');
        if (semicolon > 0 && semicolon < end) {
            end = semicolon;
        }
        int comma = value.indexOf(',');
        if (comma > 0 && comma < end) {
            end = comma;
        }
        int space = value.indexOf(' ');
        if (space > 0 && space < end) {
            end = space;
        }
        return end;
    }

    /**
     * Extract Stripe customer ID from providerData string
     * ProviderData format: "pm_abc123;customerId=cus_xyz789" or just "pm_abc123"
     */
    private String extractCustomerId(String providerData) {
        if (providerData == null || providerData.isBlank()) {
            return null;
        }

        String[] parts = providerData.split("[;,]");
        for (String part : parts) {
            String value = part.trim();
            if (value.startsWith("customerId=")) {
                return value.substring("customerId=".length()).trim();
            }
            if (value.startsWith("stripeCustomerId=")) {
                return value.substring("stripeCustomerId=".length()).trim();
            }
        }
        return null;
    }

    /**
     * Create a Stripe PaymentIntent without attaching a payment method (manual
     * confirmation flow)
     */
    public PaymentIntent createManualPaymentIntent(Payment payment, Map<String, Object> additionalData,
            com.pdh.payment.dto.StripePaymentIntentRequest request) throws StripeException {
        String currency = payment.getCurrency() != null
                ? payment.getCurrency().toLowerCase()
                : stripeConfig.getSettings().getCurrency();

        long amountInMinorUnits = toStripeAmount(payment.getAmount(), currency);

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(currency)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.valueOf(
                        stripeConfig.getSettings().getCaptureMethod().toUpperCase()))
                .setDescription(payment.getDescription());

        String confirmationMethod = stripeConfig.getSettings().getConfirmationMethod();
        if (confirmationMethod != null && !"automatic".equalsIgnoreCase(confirmationMethod)) {
            paramsBuilder.setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.valueOf(
                    confirmationMethod.toUpperCase()));
        }

        // Align payment method types with Stripe Elements usage
        paramsBuilder.addPaymentMethodType(resolveStripePaymentMethod(request.getPaymentMethodType()));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", payment.getPaymentId().toString());
        metadata.put("booking_id", payment.getBookingId().toString());
        metadata.put("user_id", payment.getUserId().toString());
        if (payment.getSagaId() != null) {
            metadata.put("saga_id", payment.getSagaId());
        }
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> {
                if (value != null) {
                    metadata.put(key, value);
                }
            });
        }
        metadata.put("save_payment_method", String.valueOf(Boolean.TRUE.equals(request.getSavePaymentMethod())));
        metadata.put("set_as_default", String.valueOf(Boolean.TRUE.equals(request.getSetAsDefault())));
        if (request.getPaymentMethodType() != null) {
            metadata.put("requested_payment_method_type", request.getPaymentMethodType().name());
        }
        paramsBuilder.putAllMetadata(metadata);

        // Handle customer information - ensure we have a customer ID if possible to
        // prevent payment method reuse issues
        String customerId = request.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            // If no customer ID is provided, try to get from additional data or create one
            String customerEmail = request.getCustomerEmail();
            if (customerEmail == null && additionalData != null) {
                customerEmail = (String) additionalData.get("customer_email");
            }

            if (customerEmail != null) {
                customerId = getOrCreateCustomer(customerEmail,
                        request.getCustomerName() != null ? request.getCustomerName()
                                : additionalData != null ? (String) additionalData.get("customer_name") : null);
            }
        }

        // Always attach customer and set setup_future_usage if a customer is available.
        // This ensures consistency with the frontend Elements provider.
        // The decision to actually save the payment method in our DB is handled
        // separately after payment success.
        if (customerId != null && !customerId.isBlank()) {
            paramsBuilder.setCustomer(customerId);
            paramsBuilder.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.ON_SESSION);
        }

        if (additionalData != null) {
            if (additionalData.containsKey("customer_email")) {
                paramsBuilder.setReceiptEmail((String) additionalData.get("customer_email"));
            }
            if (additionalData.containsKey("customer_name")) {
                paramsBuilder.putMetadata("customer_name", (String) additionalData.get("customer_name"));
            }
        }

        return PaymentIntent.create(paramsBuilder.build());
    }

    /**
     * Update an existing Stripe PaymentIntent without confirming
     */
    public PaymentIntent updateManualPaymentIntent(PaymentIntent existingIntent, Payment payment,
            Map<String, Object> additionalData,
            com.pdh.payment.dto.StripePaymentIntentRequest request) throws StripeException {
        long amountInMinorUnits = toStripeAmount(payment.getAmount(), existingIntent.getCurrency());

        PaymentIntentUpdateParams.Builder paramsBuilder = PaymentIntentUpdateParams.builder()
                .setAmount(amountInMinorUnits)
                .setDescription(payment.getDescription());

        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> {
                if (value != null) {
                    paramsBuilder.putMetadata(key, value);
                }
            });
        }

        paramsBuilder.putMetadata("save_payment_method",
                String.valueOf(Boolean.TRUE.equals(request.getSavePaymentMethod())));
        paramsBuilder.putMetadata("set_as_default", String.valueOf(Boolean.TRUE.equals(request.getSetAsDefault())));
        if (request.getPaymentMethodType() != null) {
            paramsBuilder.putMetadata("requested_payment_method_type", request.getPaymentMethodType().name());
        }

        if (additionalData != null && additionalData.containsKey("customer_email")) {
            paramsBuilder.setReceiptEmail((String) additionalData.get("customer_email"));
        }

        return existingIntent.update(paramsBuilder.build());
    }

    public void populateTransactionFromIntent(PaymentTransaction transaction, PaymentIntent paymentIntent) {
        updateTransactionWithStripeData(transaction, paymentIntent);
    }

    private void updateTransactionWithStripeData(PaymentTransaction transaction, PaymentIntent paymentIntent) {
        transaction.setGatewayTransactionId(paymentIntent.getId());
        transaction.setGatewayReference("stripe_pi_" + paymentIntent.getId());
        transaction.setGatewayResponse(paymentIntent.toJson());

        // Update status based on Stripe status
        updateTransactionStatusFromStripe(transaction, paymentIntent);
    }

    private void updateTransactionStatusFromStripe(PaymentTransaction transaction, PaymentIntent paymentIntent) {
        String stripeStatus = paymentIntent.getStatus();
        transaction.setGatewayStatus(stripeStatus.toUpperCase());

        switch (stripeStatus) {
            case "succeeded" -> {
                transaction.setStatus(PaymentStatus.COMPLETED);
                transaction.markAsCompleted();
            }
            case "processing" -> transaction.setStatus(PaymentStatus.PROCESSING);
            case "requires_payment_method", "requires_confirmation", "requires_action" ->
                transaction.setStatus(PaymentStatus.PENDING);
            case "canceled" -> {
                transaction.setStatus(PaymentStatus.CANCELLED);
                transaction.setFailureReason("Payment cancelled");
                transaction.setFailureCode("CANCELLED");
            }
            case "payment_failed" -> {
                transaction.setStatus(PaymentStatus.FAILED);
                transaction.setFailureReason("Payment failed");
                transaction.setFailureCode("PAYMENT_FAILED");
            }
            default -> {
                log.warn("Unknown Stripe status: {} for transaction: {}", stripeStatus, transaction.getTransactionId());
                transaction.setStatus(PaymentStatus.PROCESSING);
            }
        }
    }

    private PaymentTransaction createRefundTransaction(PaymentTransaction originalTransaction,
            BigDecimal refundAmount, String reason) {
        PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPayment(originalTransaction.getPayment());
        refundTransaction.setTransactionReference(
                PaymentTransaction.generateTransactionReference(PaymentTransactionType.REFUND));
        refundTransaction.setTransactionType(PaymentTransactionType.REFUND);
        refundTransaction.setStatus(PaymentStatus.PROCESSING);
        refundTransaction.setAmount(refundAmount);
        refundTransaction.setCurrency(originalTransaction.getCurrency());
        refundTransaction.setDescription("Stripe refund: " + reason);
        refundTransaction.setProvider(PaymentProvider.STRIPE);
        refundTransaction.setSagaId(originalTransaction.getSagaId());
        refundTransaction.setSagaStep("STRIPE_REFUND_PROCESSING");
        refundTransaction.setOriginalTransaction(originalTransaction);
        refundTransaction.setIsCompensation(true);

        return refundTransaction;
    }

    private Refund createStripeRefund(PaymentTransaction originalTransaction,
            BigDecimal refundAmount, String reason) throws StripeException {

        long amountInMinorUnits = toStripeAmount(refundAmount, originalTransaction.getCurrency());

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(originalTransaction.getGatewayTransactionId())
                .setAmount(amountInMinorUnits)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("original_transaction_id", originalTransaction.getTransactionId().toString())
                .putMetadata("refund_reason", reason)
                .build();

        return Refund.create(params);
    }

    private void updateRefundTransactionWithStripeData(PaymentTransaction refundTransaction, Refund refund) {
        refundTransaction.setGatewayTransactionId(refund.getId());
        refundTransaction.setGatewayReference("stripe_re_" + refund.getId());
        refundTransaction.setGatewayResponse(refund.toJson());
        refundTransaction.setGatewayStatus(refund.getStatus().toUpperCase());

        // Stripe refunds are usually successful immediately
        if ("succeeded".equals(refund.getStatus())) {
            refundTransaction.setStatus(PaymentStatus.REFUND_COMPLETED);
            refundTransaction.markAsCompleted();
        } else if ("pending".equals(refund.getStatus())) {
            refundTransaction.setStatus(PaymentStatus.REFUND_PROCESSING);
        } else if ("failed".equals(refund.getStatus())) {
            refundTransaction.setStatus(PaymentStatus.REFUND_FAILED);
            refundTransaction.markAsFailed("Refund failed", "REFUND_FAILED");
        }
    }

    /**
     * Create PaymentIntent with simple retry logic (MVP approach)
     */
    private PaymentIntent createPaymentIntentWithRetry(Payment payment, PaymentMethod paymentMethod,
            Map<String, Object> additionalData) throws StripeException {
        int maxRetries = 2;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                return createPaymentIntent(payment, paymentMethod, additionalData);
            } catch (StripeException e) {
                if (attempt >= maxRetries || !isRetryableError(e)) {
                    throw e;
                }
                log.warn("Stripe payment attempt {} failed, retrying... Error: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(1000); // Simple 1 second delay
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Max retries exceeded");
    }

    /**
     * Create Stripe Refund with simple retry logic (MVP approach)
     */
    private Refund createStripeRefundWithRetry(PaymentTransaction originalTransaction,
            BigDecimal refundAmount, String reason) throws StripeException {
        int maxRetries = 2;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                return createStripeRefund(originalTransaction, refundAmount, reason);
            } catch (StripeException e) {
                if (attempt >= maxRetries || !isRetryableError(e)) {
                    throw e;
                }
                log.warn("Stripe refund attempt {} failed, retrying... Error: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(1000); // Simple 1 second delay
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Max retries exceeded");
    }

    /**
     * Simple retry logic - only retry on rate limits and connection errors
     */
    private boolean isRetryableError(StripeException e) {
        return e.getStatusCode() >= 500 || // Server errors
                e.getClass().getSimpleName().equals("RateLimitException") ||
                e.getClass().getSimpleName().equals("ApiConnectionException");
    }

    private long toStripeAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        String effectiveCurrency = currency != null
                ? currency.toLowerCase()
                : stripeConfig.getSettings().getCurrency().toLowerCase();

        BigDecimal normalizedAmount = ZERO_DECIMAL_CURRENCIES.contains(effectiveCurrency)
                ? amount.setScale(0, RoundingMode.HALF_UP)
                : amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);

        return normalizedAmount.longValueExact();
    }

    private String resolveStripePaymentMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            return "card";
        }

        return switch (methodType) {
            case CREDIT_CARD, DEBIT_CARD -> "card";
            case APPLE_PAY -> "card"; // Apple Pay surfaces as card via Elements
            case GOOGLE_PAY, SAMSUNG_PAY -> "card";
            case PAYPAL -> "paypal";
            case MOMO, ZALOPAY, VNPAY, VIETQR -> "card"; // Placeholder, adjust when supported directly
            case BANK_TRANSFER, INTERNET_BANKING -> "card";
            case BITCOIN, ETHEREUM -> "card";
            case KLARNA -> "klarna";
            case AFTERPAY -> "afterpay_clearpay";
            case CASH_ON_DELIVERY, GIFT_CARD, OTHER -> "card";
        };
    }

    /**
     * Helper method to get or create a Stripe customer based on email
     */
    private String getOrCreateCustomer(String email, String name) {
        try {
            // Try to find customer by email first
            com.stripe.model.Customer retrievedCustomer = com.stripe.model.Customer.list(
                    com.stripe.param.CustomerListParams.builder()
                            .setEmail(email)
                            .setLimit(1L)
                            .build())
                    .getData().stream().findFirst().orElse(null);

            if (retrievedCustomer != null) {
                return retrievedCustomer.getId();
            }

            // Create a new customer if not found
            com.stripe.param.CustomerCreateParams.Builder customerParamsBuilder = com.stripe.param.CustomerCreateParams
                    .builder()
                    .setEmail(email);

            if (name != null && !name.isBlank()) {
                customerParamsBuilder.setName(name);
            }

            com.stripe.model.Customer newCustomer = com.stripe.model.Customer.create(
                    customerParamsBuilder.build());

            return newCustomer.getId();

        } catch (StripeException e) {
            log.warn("Failed to get or create customer for email: {}", email, e);
            return null; // Return null if customer creation fails
        }
    }

    /**
     * Simple error handling for payments
     */
    private void handleStripeError(PaymentTransaction transaction, StripeException e) {
        String errorMessage = getUserFriendlyMessage(e);

        transaction.setStatus(PaymentStatus.FAILED);
        transaction.setGatewayStatus("ERROR");
        transaction.setFailureReason(errorMessage);
        transaction.setFailureCode(e.getCode() != null ? e.getCode() : "STRIPE_ERROR");
        transaction.setGatewayResponse(e.getMessage());
        transaction.setProcessedAt(ZonedDateTime.now());
    }

    /**
     * Simple error handling for refunds
     */
    private void handleStripeRefundError(PaymentTransaction refundTransaction, StripeException e) {
        String errorMessage = getUserFriendlyMessage(e);

        refundTransaction.setStatus(PaymentStatus.REFUND_FAILED);
        refundTransaction.setGatewayStatus("ERROR");
        refundTransaction.setFailureReason(errorMessage);
        refundTransaction.setFailureCode(e.getCode() != null ? e.getCode() : "STRIPE_REFUND_ERROR");
        refundTransaction.setGatewayResponse(e.getMessage());
        refundTransaction.setProcessedAt(ZonedDateTime.now());
    }

    /**
     * Convert Stripe errors to user-friendly messages (MVP approach)
     */
    private String getUserFriendlyMessage(StripeException e) {
        String errorCode = e.getCode();
        if (errorCode == null) {
            return "Payment processing failed. Please try again.";
        }

        return switch (errorCode) {
            case "card_declined" -> "Your card was declined. Please try a different payment method.";
            case "expired_card" -> "Your card has expired. Please use a different payment method.";
            case "incorrect_cvc" -> "The security code you entered is incorrect.";
            case "incorrect_number" -> "The card number you entered is incorrect.";
            case "insufficient_funds" -> "Your card has insufficient funds.";
            case "processing_error" -> "A processing error occurred. Please try again.";
            case "authentication_required" -> "Additional authentication is required for this payment.";
            default -> "Payment processing failed. Please try again or contact support.";
        };
    }
}

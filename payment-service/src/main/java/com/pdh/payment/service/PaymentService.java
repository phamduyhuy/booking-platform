package com.pdh.payment.service;

import com.pdh.payment.dto.StripePaymentIntentRequest;
import com.pdh.payment.dto.StripePaymentIntentResponse;
import com.pdh.payment.dto.request.AddPaymentMethodRequest;
import com.pdh.payment.model.Payment;
import com.pdh.payment.model.PaymentMethod;
import com.pdh.payment.model.PaymentTransaction;
import com.pdh.payment.model.enums.PaymentProvider;
import com.pdh.payment.model.enums.PaymentMethodType;
import com.pdh.payment.model.enums.PaymentStatus;
import com.pdh.payment.model.enums.PaymentTransactionType;
import com.pdh.common.outbox.service.OutboxEventService;
import com.pdh.payment.repository.PaymentRepository;
import com.pdh.payment.repository.PaymentTransactionRepository;
import com.pdh.payment.repository.PaymentMethodRepository;
import com.pdh.payment.service.PaymentMethodService;
import com.pdh.payment.service.strategy.impl.StripePaymentStrategy;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OutboxEventService eventPublisher;
    private final PaymentContext paymentContext;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodService paymentMethodService;
    private final StripePaymentStrategy stripePaymentStrategy;

    /**
     * Process payment using Strategy Pattern
     */
    @Transactional
    public PaymentTransaction processPayment(Payment payment, PaymentMethod paymentMethod,
            Map<String, Object> additionalData) {
        log.info("Processing payment {} using payment method: {}",
                payment.getPaymentId(), paymentMethod.getProvider());

        try {
            // Save payment first
            Payment savedPayment = paymentRepository.save(payment);

            // Process payment using strategy
            PaymentTransaction transaction = paymentContext.processPayment(savedPayment, paymentMethod, additionalData);

            // Save transaction
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

            // Sync payment status with transaction status (critical for MCP/server-side
            // payments)
            syncPaymentStatusWithTransaction(savedPayment, savedTransaction);

            // Publish payment event
            publishPaymentEvent(savedPayment, savedTransaction, "PaymentInitiated");

            // If payment completed immediately, publish completion event
            if (savedTransaction.getStatus().isSuccessful()) {
                publishPaymentEvent(savedPayment, savedTransaction, "PaymentProcessed");
            }

            log.info("Payment processing completed for payment: {}", payment.getPaymentId());
            return savedTransaction;

        } catch (Exception e) {
            log.error("Payment processing failed for payment: {}", payment.getPaymentId(), e);

            // Create failed transaction record
            PaymentTransaction failedTransaction = createFailedTransaction(payment, e.getMessage());
            PaymentTransaction savedFailedTransaction = paymentTransactionRepository.save(failedTransaction);

            // Publish failure event
            publishPaymentEvent(payment, savedFailedTransaction, "PaymentFailed");

            throw e;
        }
    }

    /**
     * Initialize payment records for a user-driven confirmation flow
     */
    @Transactional
    public PaymentTransaction initializePayment(Payment payment, Map<String, Object> additionalData) {
        log.info("Initializing payment record for booking: {}", payment.getBookingId());

        Payment persistedPayment = paymentRepository.findByBookingId(payment.getBookingId())
                .map(existing -> updatePendingPayment(existing, payment))
                .orElseGet(() -> {
                    payment.setPaymentReference(Payment.generatePaymentReference());
                    payment.setStatus(PaymentStatus.PENDING);
                    return paymentRepository.save(payment);
                });

        PaymentTransaction transaction = paymentTransactionRepository
                .findByPayment_PaymentIdAndTransactionType(persistedPayment.getPaymentId(),
                        PaymentTransactionType.PAYMENT)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    PaymentTransaction newTransaction = new PaymentTransaction();
                    newTransaction.setPayment(persistedPayment);
                    newTransaction.setTransactionReference(
                            PaymentTransaction.generateTransactionReference(PaymentTransactionType.PAYMENT));
                    newTransaction.setTransactionType(PaymentTransactionType.PAYMENT);
                    return newTransaction;
                });

        transaction.setAmount(persistedPayment.getAmount());
        transaction.setCurrency(persistedPayment.getCurrency());
        transaction.setDescription(persistedPayment.getDescription());
        transaction.setProvider(persistedPayment.getProvider());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setSagaId(persistedPayment.getSagaId());
        transaction.setSagaStep("PAYMENT_INITIALIZED");

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        publishPaymentEvent(persistedPayment, savedTransaction, "PaymentInitialized");

        log.info("Payment initialized for booking: {} with transaction: {}",
                persistedPayment.getBookingId(), savedTransaction.getTransactionId());

        return savedTransaction;
    }

    /**
     * Create or refresh a Stripe PaymentIntent for manual confirmation flows
     */
    @Transactional
    public StripePaymentIntentResponse createStripePaymentIntent(StripePaymentIntentRequest request, UUID userId) {
        try {
            Payment basePayment = paymentRepository.findByBookingId(request.getBookingId())
                    .orElseGet(() -> buildPaymentFromRequest(request, userId));
            basePayment.setUserId(userId);

            applyRequestUpdates(basePayment, request);
            Payment persistedPayment = paymentRepository.save(basePayment);

            PaymentTransaction transaction = paymentTransactionRepository
                    .findByPayment_PaymentIdAndTransactionType(persistedPayment.getPaymentId(),
                            PaymentTransactionType.PAYMENT)
                    .stream()
                    .findFirst()
                    .orElseGet(() -> {
                        PaymentTransaction newTransaction = new PaymentTransaction();
                        newTransaction.setPayment(persistedPayment);
                        newTransaction.setTransactionReference(
                                PaymentTransaction.generateTransactionReference(PaymentTransactionType.PAYMENT));
                        newTransaction.setTransactionType(PaymentTransactionType.PAYMENT);
                        return newTransaction;
                    });

            transaction.setAmount(persistedPayment.getAmount());
            transaction.setCurrency(persistedPayment.getCurrency());
            transaction.setDescription(persistedPayment.getDescription());
            transaction.setProvider(PaymentProvider.STRIPE);
            transaction.setStatus(PaymentStatus.PENDING);
            transaction.setSagaId(persistedPayment.getSagaId());
            transaction.setSagaStep("STRIPE_PAYMENT_PENDING");

            Map<String, Object> additionalData = buildAdditionalStripeData(request);

            PaymentIntent paymentIntent = createOrUpdateStripeIntent(persistedPayment, transaction, request,
                    additionalData);

            persistedPayment.setGatewayTransactionId(paymentIntent.getId());
            persistedPayment.setGatewayStatus(paymentIntent.getStatus().toUpperCase());
            persistedPayment.setGatewayResponse(paymentIntent.toJson());
            persistedPayment.setStatus(resolvePaymentStatus(paymentIntent.getStatus()));
            paymentRepository.save(persistedPayment);

            stripePaymentStrategy.populateTransactionFromIntent(transaction, paymentIntent);
            transaction.setAmount(persistedPayment.getAmount());
            transaction.setCurrency(persistedPayment.getCurrency());
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

            Map<String, Object> metadata = new HashMap<>();
            if (paymentIntent.getMetadata() != null) {
                metadata.putAll(paymentIntent.getMetadata());
            }

            return StripePaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .clientSecret(paymentIntent.getClientSecret())
                    .status(paymentIntent.getStatus())
                    .amount(persistedPayment.getAmount())
                    .currency(persistedPayment.getCurrency())
                    .description(persistedPayment.getDescription())
                    .transactionId(savedTransaction.getTransactionId())
                    .createdAt(convertStripeTimestamp(paymentIntent.getCreated()))
                    .metadata(metadata)
                    .build();

        } catch (StripeException e) {
            log.error("Failed to create Stripe payment intent for booking: {}", request.getBookingId(), e);
            throw new RuntimeException("Failed to create Stripe payment intent: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentTransaction getTransactionByGatewayIntentId(String paymentIntentId) {
        return paymentTransactionRepository.findByGatewayTransactionId(paymentIntentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for payment intent: " + paymentIntentId));
    }

    /**
     * Process refund using Strategy Pattern
     */
    @Transactional
    public PaymentTransaction processRefund(UUID originalTransactionId, BigDecimal refundAmount, String reason) {
        log.info("Processing refund for transaction: {} with amount: {}", originalTransactionId, refundAmount);

        Optional<PaymentTransaction> originalTransactionOpt = paymentTransactionRepository
                .findById(originalTransactionId);
        if (originalTransactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Original transaction not found: " + originalTransactionId);
        }

        PaymentTransaction originalTransaction = originalTransactionOpt.get();

        try {
            // Process refund using strategy
            PaymentTransaction refundTransaction = paymentContext.processRefund(originalTransaction, refundAmount,
                    reason);

            // Save refund transaction
            PaymentTransaction savedRefundTransaction = paymentTransactionRepository.save(refundTransaction);

            // Publish refund event
            publishRefundEvent(originalTransaction.getPayment(), savedRefundTransaction, "PaymentRefunded");

            // If refund completed immediately, publish completion event
            if (savedRefundTransaction.getStatus() == PaymentStatus.REFUND_COMPLETED) {
                publishRefundEvent(originalTransaction.getPayment(), savedRefundTransaction, "PaymentRefunded");
            }

            log.info("Refund processing completed for transaction: {}", originalTransactionId);
            return savedRefundTransaction;

        } catch (Exception e) {
            log.error("Refund processing failed for transaction: {}", originalTransactionId, e);
            throw e;
        }
    }

    /**
     * Verify payment status using Strategy Pattern
     */
    @Transactional
    public PaymentTransaction verifyPaymentStatus(UUID transactionId) {
        log.debug("Verifying payment status for transaction: {}", transactionId);

        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        PaymentTransaction transaction = transactionOpt.get();
        PaymentStatus previousStatus = transaction.getStatus();

        PaymentTransaction updatedTransaction = paymentContext.verifyPaymentStatus(transaction);

        // Save updated transaction
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(updatedTransaction);

        boolean becameSuccessful = savedTransaction.getStatus().isSuccessful()
                && (previousStatus == null || !previousStatus.isSuccessful());

        if (becameSuccessful) {
            storeStripePaymentMethodIfNeeded(savedTransaction);
        }

        // Update payment status when transaction succeeds
        if (savedTransaction.getStatus().isSuccessful()) {
            Payment payment = savedTransaction.getPayment();
            if (payment != null && payment.getStatus() != PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.COMPLETED);
                paymentRepository.save(payment);
            }
        }

        // Publish status update event if status changed
        if (!Objects.equals(previousStatus, savedTransaction.getStatus())) {
            if (savedTransaction.getStatus().isSuccessful()) {
                publishPaymentEvent(savedTransaction.getPayment(), savedTransaction, "PaymentProcessed");
            } else if (savedTransaction.getStatus() == PaymentStatus.FAILED) {
                publishPaymentEvent(savedTransaction.getPayment(), savedTransaction, "PaymentFailed");
            }
        }

        return savedTransaction;
    }

    @Transactional
    public PaymentTransaction confirmStripePayment(UUID transactionId, UUID bookingId) {
        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        PaymentTransaction existingTransaction = transactionOpt.get();
        if (bookingId != null && existingTransaction.getPayment() != null
                && existingTransaction.getPayment().getBookingId() != null
                && !existingTransaction.getPayment().getBookingId().equals(bookingId)) {
            throw new IllegalArgumentException("Transaction does not belong to booking: " + bookingId);
        }

        boolean alreadySuccessful = existingTransaction.getStatus().isSuccessful();

        PaymentTransaction transaction = verifyPaymentStatus(transactionId);
        if (!transaction.getStatus().isSuccessful()) {
            throw new IllegalStateException("Payment not completed for transaction: " + transactionId);
        }

        if (alreadySuccessful) {
            emitPaymentProcessedEvent(transaction);
        }

        return transaction;
    }

    /**
     * Cancel payment using Strategy Pattern
     */
    @Transactional
    public PaymentTransaction cancelPayment(UUID transactionId, String reason) {
        log.info("Cancelling payment for transaction: {} with reason: {}", transactionId, reason);

        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        PaymentTransaction transaction = transactionOpt.get();
        PaymentTransaction cancelledTransaction = paymentContext.cancelPayment(transaction, reason);

        // Save cancelled transaction
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(cancelledTransaction);

        // Publish cancellation event
        publishPaymentEvent(savedTransaction.getPayment(), savedTransaction, "PaymentCancelled");

        return savedTransaction;
    }

    /**
     * Get processing fee for payment method
     */
    public BigDecimal getProcessingFee(BigDecimal amount, PaymentMethod paymentMethod) {
        return paymentContext.getProcessingFee(amount, paymentMethod);
    }

    /**
     * Legacy method for backward compatibility - process payment by booking ID
     */
    @Transactional
    public void processPayment(UUID bookingId) {
        log.info("Processing payment for booking: {} (legacy method)", bookingId);
        // This is kept for backward compatibility with existing saga orchestration
        eventPublisher.publishEvent("PaymentProcessed", "Booking", bookingId.toString(),
                Map.of("bookingId", bookingId));
    }

    /**
     * Legacy method for backward compatibility - refund payment by booking ID
     */
    @Transactional
    public void refundPayment(UUID bookingId) {
        log.info("Refunding payment for booking: {} (legacy method)", bookingId);
        // This is kept for backward compatibility with existing saga orchestration
        eventPublisher.publishEvent("PaymentRefunded", "Booking", bookingId.toString(), Map.of("bookingId", bookingId));
    }

    /**
     * Refund payment by payment ID and amount
     */
    @Transactional
    public PaymentTransaction refundPayment(UUID paymentId, BigDecimal refundAmount, String reason) {
        log.info("Processing refund for payment: {} with amount: {}", paymentId, refundAmount);

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();

        // Find the latest successful transaction
        Optional<PaymentTransaction> latestTransaction = payment.getTransactions().stream()
                .filter(t -> t.getStatus().isSuccessful())
                .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()));

        if (latestTransaction.isEmpty()) {
            throw new IllegalArgumentException("No successful transaction found for payment: " + paymentId);
        }

        return processRefund(latestTransaction.get().getTransactionId(), refundAmount, reason);
    }

    /**
     * Cancel payment by payment ID
     */
    @Transactional
    public void cancelPayment(UUID paymentId) {
        log.info("Cancelling payment: {}", paymentId);

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();

        // Update payment status
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        // Find pending transactions and cancel them
        payment.getTransactions().stream()
                .filter(t -> t.getStatus() == PaymentStatus.PENDING)
                .forEach(transaction -> {
                    transaction.setStatus(PaymentStatus.CANCELLED);
                    transaction.setFailureReason("Payment cancelled");
                    paymentTransactionRepository.save(transaction);
                });

        log.info("Payment cancelled successfully: {}", paymentId);
    }

    /**
     * Confirm payment by payment ID
     */
    @Transactional
    public void confirmPayment(UUID paymentId) {
        log.info("Confirming payment: {}", paymentId);

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();

        // Update payment status
        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        // Find pending transactions and mark as completed
        payment.getTransactions().stream()
                .filter(t -> t.getStatus() == PaymentStatus.PENDING)
                .forEach(transaction -> {
                    transaction.setStatus(PaymentStatus.COMPLETED);
                    paymentTransactionRepository.save(transaction);
                });

        log.info("Payment confirmed successfully: {}", paymentId);
    }

    // Helper methods

    /**
     * Sync Payment status with Transaction status
     * Critical for MCP/server-side payments where transaction completes immediately
     */
    private void syncPaymentStatusWithTransaction(Payment payment, PaymentTransaction transaction) {
        PaymentStatus transactionStatus = transaction.getStatus();

        // If transaction succeeded, mark payment as completed
        if (transactionStatus != null && transactionStatus.isSuccessful()) {
            log.info("Syncing payment {} to COMPLETED based on successful transaction {}",
                    payment.getPaymentId(), transaction.getTransactionId());
            payment.markAsConfirmed();
            paymentRepository.save(payment);
        }
        // If transaction failed, mark payment as failed
        else if (transactionStatus == PaymentStatus.FAILED ||
                transactionStatus == PaymentStatus.DECLINED ||
                transactionStatus == PaymentStatus.ERROR) {
            log.info("Syncing payment {} to FAILED based on failed transaction {}",
                    payment.getPaymentId(), transaction.getTransactionId());
            payment.markAsFailed(
                    transaction.getFailureReason() != null ? transaction.getFailureReason() : "Transaction failed");
            paymentRepository.save(payment);
        }
        // If transaction cancelled
        else if (transactionStatus == PaymentStatus.CANCELLED) {
            log.info("Syncing payment {} to CANCELLED based on cancelled transaction {}",
                    payment.getPaymentId(), transaction.getTransactionId());
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason("Payment cancelled");
            paymentRepository.save(payment);
        }
        // Otherwise keep payment status as-is or set to transaction status if different
        else if (transactionStatus != null && payment.getStatus() != transactionStatus) {
            log.info("Syncing payment {} status from {} to {} based on transaction {}",
                    payment.getPaymentId(), payment.getStatus(), transactionStatus, transaction.getTransactionId());
            payment.setStatus(transactionStatus);
            paymentRepository.save(payment);
        }
    }

    private void publishPaymentEvent(Payment payment, PaymentTransaction transaction, String eventType) {
        Map<String, Object> eventData = Map.ofEntries(
                Map.entry("eventType", eventType),
                Map.entry("paymentId", payment.getPaymentId()),
                Map.entry("bookingId", payment.getBookingId()),
                Map.entry("userId", payment.getUserId()),
                Map.entry("transactionId", transaction.getTransactionId()),
                Map.entry("amount", transaction.getAmount()),
                Map.entry("currency", transaction.getCurrency()),
                Map.entry("status", transaction.getStatus()),
                Map.entry("provider", transaction.getProvider()),
                Map.entry("sagaId", payment.getSagaId() != null ? payment.getSagaId() : ""),
                Map.entry("gatewayTransactionId",
                        transaction.getGatewayTransactionId() != null ? transaction.getGatewayTransactionId() : ""),
                Map.entry("gatewayStatus",
                        transaction.getGatewayStatus() != null ? transaction.getGatewayStatus() : ""));

        log.info("Publishing payment event: type={}, paymentId={}, bookingId={}, sagaId={}, status={}",
                eventType, payment.getPaymentId(), payment.getBookingId(), payment.getSagaId(),
                transaction.getStatus());

        eventPublisher.publishEvent(
                eventType,
                "Payment",
                payment.getPaymentId().toString(),
                eventData);

        log.info("Payment event published successfully: type={}, paymentId={}", eventType, payment.getPaymentId());
    }

    private void storeStripePaymentMethodIfNeeded(PaymentTransaction transaction) {
        try {
            if (transaction == null || transaction.getProvider() != PaymentProvider.STRIPE) {
                return;
            }

            Payment payment = transaction.getPayment();
            if (payment == null || payment.getUserId() == null) {
                return;
            }

            String gatewayTransactionId = transaction.getGatewayTransactionId();
            if (gatewayTransactionId == null || gatewayTransactionId.isBlank()) {
                return;
            }

            PaymentIntent paymentIntent = PaymentIntent.retrieve(gatewayTransactionId);
            if (paymentIntent == null || paymentIntent.getMetadata() == null) {
                return;
            }

            Map<String, String> metadata = paymentIntent.getMetadata();
            boolean shouldSave = Boolean.parseBoolean(metadata.getOrDefault("save_payment_method", "false"));
            if (!shouldSave) {
                return;
            }

            String stripePaymentMethodId = paymentIntent.getPaymentMethod();
            if (stripePaymentMethodId == null || stripePaymentMethodId.isBlank()) {
                log.warn("Stripe payment intent {} missing payment_method for storing", gatewayTransactionId);
                return;
            }

            Optional<PaymentMethod> existingByToken = paymentMethodRepository
                    .findByTokenAndIsActiveTrue(stripePaymentMethodId);
            if (existingByToken.isPresent()) {
                PaymentMethod existing = existingByToken.get();
                if (existing.getUserId().equals(payment.getUserId())) {
                    boolean setAsDefault = Boolean.parseBoolean(metadata.getOrDefault("set_as_default", "false"));
                    if (setAsDefault && !Boolean.TRUE.equals(existing.getIsDefault())) {
                        paymentMethodService.setDefaultPaymentMethod(payment.getUserId(), existing.getMethodId());
                    }
                } else {
                    log.warn("Stripe payment method {} already associated with another user ({}), skipping save",
                            stripePaymentMethodId, existing.getUserId());
                }
                return;
            }

            com.stripe.model.PaymentMethod stripeMethod = com.stripe.model.PaymentMethod
                    .retrieve(stripePaymentMethodId);
            if (stripeMethod == null) {
                return;
            }

            PaymentMethodType methodType = determinePaymentMethodType(stripeMethod, paymentIntent);
            boolean setAsDefault = Boolean.parseBoolean(metadata.getOrDefault("set_as_default", "false"));

            AddPaymentMethodRequest.AddPaymentMethodRequestBuilder builder = AddPaymentMethodRequest.builder()
                    .methodType(methodType)
                    .provider(PaymentProvider.STRIPE)
                    .displayName(buildDisplayName(stripeMethod, methodType))
                    .stripePaymentMethodId(stripePaymentMethodId)
                    .stripeCustomerId(paymentIntent.getCustomer())
                    .setAsDefault(setAsDefault);

            com.stripe.model.PaymentMethod.Card card = stripeMethod.getCard();
            if (card != null) {
                if (card.getLast4() != null) {
                    builder.cardLastFour(card.getLast4());
                }
                if (card.getBrand() != null) {
                    builder.cardBrand(card.getBrand());
                }
                if (card.getExpMonth() != null) {
                    builder.cardExpiryMonth(card.getExpMonth().intValue());
                }
                if (card.getExpYear() != null) {
                    builder.cardExpiryYear(card.getExpYear().intValue());
                }
            }

            com.stripe.model.PaymentMethod.BillingDetails billing = stripeMethod.getBillingDetails();
            String cardHolderName = null;
            String cardHolderEmail = null;
            if (billing != null) {
                if (billing.getName() != null) {
                    cardHolderName = billing.getName();
                }
                if (billing.getEmail() != null) {
                    cardHolderEmail = billing.getEmail();
                }
            }

            if (cardHolderName == null && paymentIntent.getShipping() != null
                    && paymentIntent.getShipping().getName() != null) {
                cardHolderName = paymentIntent.getShipping().getName();
            }

            if (cardHolderName != null) {
                builder.cardHolderName(cardHolderName);
            }
            if (cardHolderEmail != null) {
                builder.cardHolderEmail(cardHolderEmail);
            }

            paymentMethodService.addPaymentMethod(payment.getUserId(), builder.build());
            log.info("Saved Stripe payment method {} for user {}", stripePaymentMethodId, payment.getUserId());
        } catch (StripeException e) {
            log.error("Failed to store Stripe payment method for transaction {}", transaction.getTransactionId(), e);
        } catch (Exception e) {
            log.error("Unexpected error while storing payment method for transaction {}",
                    transaction.getTransactionId(), e);
        }
    }

    private PaymentMethodType determinePaymentMethodType(com.stripe.model.PaymentMethod stripeMethod,
            PaymentIntent paymentIntent) {
        if (stripeMethod == null) {
            return PaymentMethodType.CREDIT_CARD;
        }

        if (stripeMethod.getType() != null && !stripeMethod.getType().isBlank()) {
            if ("card".equalsIgnoreCase(stripeMethod.getType())) {
                com.stripe.model.PaymentMethod.Card card = stripeMethod.getCard();
                if (card != null && card.getWallet() != null) {
                    String walletType = card.getWallet().getType();
                    if (walletType != null) {
                        walletType = walletType.toLowerCase();
                        if (walletType.contains("apple")) {
                            return PaymentMethodType.APPLE_PAY;
                        }
                        if (walletType.contains("google")) {
                            return PaymentMethodType.GOOGLE_PAY;
                        }
                        if (walletType.contains("samsung")) {
                            return PaymentMethodType.SAMSUNG_PAY;
                        }
                    }
                }
                return PaymentMethodType.CREDIT_CARD;
            }

            try {
                return PaymentMethodType.fromJson(stripeMethod.getType().toUpperCase());
            } catch (Exception ignored) {
                // Ignore and fall back
            }
        }

        if (paymentIntent != null && paymentIntent.getMetadata() != null) {
            String requestedType = paymentIntent.getMetadata().get("requested_payment_method_type");
            if (requestedType != null) {
                try {
                    return PaymentMethodType.fromJson(requestedType);
                } catch (Exception ignored) {
                    // Ignore and fall back
                }
            }
        }

        return PaymentMethodType.CREDIT_CARD;
    }

    private String buildDisplayName(com.stripe.model.PaymentMethod stripeMethod, PaymentMethodType methodType) {
        if (stripeMethod == null) {
            return methodType.getDisplayName();
        }

        if (stripeMethod.getCard() != null) {
            String brand = stripeMethod.getCard().getBrand();
            String last4 = stripeMethod.getCard().getLast4();
            if (brand != null && last4 != null) {
                return brand.toUpperCase() + " •••• " + last4;
            }
        }

        if (stripeMethod.getBillingDetails() != null && stripeMethod.getBillingDetails().getName() != null) {
            return stripeMethod.getBillingDetails().getName();
        }

        return methodType.getDisplayName();
    }

    private void emitPaymentProcessedEvent(PaymentTransaction transaction) {
        Payment payment = transaction.getPayment();
        if (payment != null) {
            publishPaymentEvent(payment, transaction, "PaymentProcessed");
        }
    }

    private Payment updatePendingPayment(Payment existing, Payment incoming) {
        existing.setAmount(incoming.getAmount());
        if (incoming.getCurrency() != null) {
            existing.setCurrency(incoming.getCurrency());
        }
        if (incoming.getDescription() != null) {
            existing.setDescription(incoming.getDescription());
        }
        if (incoming.getMethodType() != null) {
            existing.setMethodType(incoming.getMethodType());
        }
        if (incoming.getProvider() != null) {
            existing.setProvider(incoming.getProvider());
        }
        if (incoming.getSagaId() != null) {
            existing.setSagaId(incoming.getSagaId());
        }
        existing.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(existing);
    }

    private Payment buildPaymentFromRequest(StripePaymentIntentRequest request, UUID userId) {
        Payment payment = new Payment();
        payment.setPaymentReference(Payment.generatePaymentReference());
        payment.setBookingId(request.getBookingId());
        payment.setUserId(userId);
        payment.setSagaId(Optional.ofNullable(request.getSagaId()).orElse(UUID.randomUUID().toString()));
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setDescription(Optional.ofNullable(request.getDescription())
                .orElse("Payment for booking: " + request.getBookingId()));
        payment.setMethodType(request.getPaymentMethodType());
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private void applyRequestUpdates(Payment payment, StripePaymentIntentRequest request) {
        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        if (request.getCurrency() != null) {
            payment.setCurrency(request.getCurrency().toUpperCase());
        }
        if (request.getDescription() != null) {
            payment.setDescription(request.getDescription());
        }
        if (request.getSagaId() != null) {
            payment.setSagaId(request.getSagaId());
        }
        if (request.getPaymentMethodType() != null) {
            payment.setMethodType(request.getPaymentMethodType());
        }
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(PaymentStatus.PENDING);
    }

    private Map<String, Object> buildAdditionalStripeData(StripePaymentIntentRequest request) {
        Map<String, Object> data = new HashMap<>();
        if (request.getCustomerEmail() != null) {
            data.put("customer_email", request.getCustomerEmail());
        }
        if (request.getCustomerName() != null) {
            data.put("customer_name", request.getCustomerName());
        }
        if (request.getBillingAddress() != null) {
            data.put("billing_address", request.getBillingAddress());
        }
        return data;
    }

    private PaymentIntent createOrUpdateStripeIntent(Payment payment, PaymentTransaction transaction,
            StripePaymentIntentRequest request,
            Map<String, Object> additionalData) throws StripeException {
        if (transaction.getGatewayTransactionId() != null) {
            PaymentIntent existingIntent = PaymentIntent.retrieve(transaction.getGatewayTransactionId());
            if (existingIntent != null) {
                if (canReuseIntent(existingIntent, payment)) {
                    PaymentIntent updated = stripePaymentStrategy.updateManualPaymentIntent(existingIntent, payment,
                            additionalData, request);
                    transaction.setGatewayTransactionId(updated.getId());
                    return updated;
                }
                if (!isTerminalStripeStatus(existingIntent.getStatus())) {
                    existingIntent.cancel();
                }
            }
        }

        PaymentIntent newIntent = stripePaymentStrategy.createManualPaymentIntent(payment, additionalData, request);
        transaction.setGatewayTransactionId(newIntent.getId());
        return newIntent;
    }

    private boolean canReuseIntent(PaymentIntent intent, Payment payment) {
        return intent != null
                && !isTerminalStripeStatus(intent.getStatus())
                && intent.getCurrency() != null
                && intent.getCurrency().equalsIgnoreCase(payment.getCurrency());
    }

    private boolean isTerminalStripeStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status) {
            case "succeeded", "canceled" -> true;
            default -> false;
        };
    }

    private PaymentStatus resolvePaymentStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.COMPLETED;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_action", "requires_payment_method", "requires_confirmation" -> PaymentStatus.PENDING;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "payment_failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private LocalDateTime convertStripeTimestamp(Long createdEpochSeconds) {
        if (createdEpochSeconds == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(createdEpochSeconds), ZoneId.systemDefault());
    }

    private void publishRefundEvent(Payment payment, PaymentTransaction refundTransaction, String eventType) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventType", eventType);
        eventData.put("paymentId", payment.getPaymentId());
        eventData.put("bookingId", payment.getBookingId());
        eventData.put("userId", payment.getUserId());
        eventData.put("refundTransactionId", refundTransaction.getTransactionId());
        eventData.put("refundAmount", refundTransaction.getAmount());
        eventData.put("currency", refundTransaction.getCurrency());
        eventData.put("status", refundTransaction.getStatus());
        eventData.put("provider", refundTransaction.getProvider());
        eventData.put("originalTransactionId",
                refundTransaction.getOriginalTransaction() != null
                        ? refundTransaction.getOriginalTransaction().getTransactionId()
                        : "");
        eventData.put("sagaId", payment.getSagaId() != null ? payment.getSagaId() : "");

        eventPublisher.publishEvent(
                eventType,
                "Payment",
                payment.getPaymentId().toString(),
                eventData);
    }

    private PaymentTransaction createFailedTransaction(Payment payment, String errorMessage) {
        PaymentTransaction failedTransaction = new PaymentTransaction();
        failedTransaction.setPayment(payment);
        failedTransaction.setTransactionReference(
                PaymentTransaction.generateTransactionReference(PaymentTransactionType.PAYMENT));
        failedTransaction.setTransactionType(PaymentTransactionType.PAYMENT);
        failedTransaction.setStatus(PaymentStatus.FAILED);
        failedTransaction.setAmount(payment.getAmount());
        failedTransaction.setCurrency(payment.getCurrency());
        failedTransaction.setDescription("Failed payment for " + payment.getDescription());
        failedTransaction.setFailureReason(errorMessage);
        failedTransaction.setFailureCode("PROCESSING_ERROR");
        failedTransaction.setSagaId(payment.getSagaId());
        failedTransaction.setSagaStep("PAYMENT_FAILED");

        return failedTransaction;
    }

    /**
     * Get payment by booking ID
     */
    public Optional<Payment> getPaymentByBookingId(UUID bookingId) {
        log.debug("Getting payment for bookingId: {}", bookingId);
        return paymentRepository.findByBookingId(bookingId);
    }

    /**
     * Get all transactions for a payment
     */
    public java.util.List<PaymentTransaction> getPaymentTransactions(UUID paymentId) {
        log.debug("Getting transactions for paymentId: {}", paymentId);
        return paymentTransactionRepository.findByPayment_PaymentIdOrderByCreatedAtDesc(paymentId);
    }
}

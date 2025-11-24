package com.pdh.payment.controller;

import com.pdh.common.utils.AuthenticationUtils;

import com.pdh.payment.dto.*;
import com.pdh.payment.dto.StripePaymentConfirmationRequest;
import com.pdh.payment.model.Payment;
import com.pdh.payment.model.PaymentMethod;
import com.pdh.payment.model.PaymentTransaction;
import com.pdh.payment.model.enums.PaymentProvider;
import com.pdh.payment.repository.PaymentMethodRepository;
import com.pdh.payment.repository.PaymentRepository;
import com.pdh.payment.service.PaymentService;
import com.pdh.payment.service.strategy.PaymentStrategyFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.UUID;

/**
 * Enhanced Payment Controller with Strategy Pattern Support
 * Handles payment operations for Stripe, VietQR, and other gateways
 */
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory strategyFactory;

    /**
     * Process payment using Strategy Pattern - Reactive implementation
     */
    @PostMapping("/process-payment")

    public ResponseEntity<Map<String, Object>> processPaymentWithStrategy(
            @Valid @RequestBody PaymentProcessRequest request) {
        try {
            UUID userId = AuthenticationUtils.getCurrentUserIdFromContext();

            // Convert to internal payment request
            PaymentRequest paymentRequest = request.toPaymentRequest(userId);

            // Create payment entity
            Payment payment = createPaymentFromRequest(paymentRequest);

            // Create or get payment method
            PaymentMethod paymentMethod = createOrGetPaymentMethod(paymentRequest, userId);

            // Process payment using strategy
            PaymentTransaction transaction = paymentService.processPayment(payment, paymentMethod,
                    request.getAdditionalData());

            Map<String, Object> response = Map.of(
                    "success", true,
                    "transactionId", transaction.getTransactionId(),
                    "status", transaction.getStatus(),
                    "amount", transaction.getAmount(),
                    "currency", transaction.getCurrency(),
                    "gatewayTransactionId",
                    transaction.getGatewayTransactionId() != null ? transaction.getGatewayTransactionId() : "",
                    "message", "Payment processed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Payment processing failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Payment processing failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Create Stripe PaymentIntent for frontend integration
     */
    @PostMapping("/stripe/create-payment-intent")
    public ResponseEntity<StripePaymentIntentResponse> createStripePaymentIntent(
            @Valid @RequestBody StripePaymentIntentRequest request) {
        try {
            UUID userId = AuthenticationUtils.getCurrentUserIdFromContext();

            StripePaymentIntentResponse response = paymentService.createStripePaymentIntent(request, userId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Stripe PaymentIntent creation failed", e);
            StripePaymentIntentResponse errorResponse = StripePaymentIntentResponse.builder()
                    .error(StripePaymentIntentResponse.StripeErrorDto.builder()
                            .message(e.getMessage())
                            .type("api_error")
                            .build())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Confirm Stripe PaymentIntent
     */
    @PostMapping("/stripe/confirm-payment-intent/{paymentIntentId}")
    public ResponseEntity<Map<String, Object>> confirmStripePaymentIntent(
            @PathVariable String paymentIntentId,
            @RequestBody Map<String, Object> request) {
        try {
            PaymentTransaction transaction = paymentService.getTransactionByGatewayIntentId(paymentIntentId);
            PaymentTransaction updatedTransaction = paymentService.verifyPaymentStatus(transaction.getTransactionId());

            boolean success = updatedTransaction.getStatus().isSuccessful();

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("paymentIntentId", paymentIntentId);
            response.put("transactionId", updatedTransaction.getTransactionId());
            response.put("status", updatedTransaction.getStatus());
            response.put("gatewayStatus", updatedTransaction.getGatewayStatus());
            response.put("message",
                    success ? "Payment intent confirmed successfully" : "Payment intent requires additional action");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException notFound) {
            log.error("Stripe PaymentIntent confirmation failed: {}", notFound.getMessage());
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", notFound.getMessage(),
                    "message", "Payment intent not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (Exception e) {
            log.error("Stripe PaymentIntent confirmation failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Payment confirmation failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Confirm completed Stripe payment (user-driven flow)
     */
    @PostMapping("/stripe/confirm-payment")
    public ResponseEntity<Map<String, Object>> confirmStripePayment(
            @Valid @RequestBody StripePaymentConfirmationRequest request) {
        try {
            PaymentTransaction transaction = paymentService.confirmStripePayment(request.getTransactionId(),
                    request.getBookingId());

            Map<String, Object> response = Map.of(
                    "success", true,
                    "transactionId", transaction.getTransactionId(),
                    "paymentIntentId", transaction.getGatewayTransactionId(),
                    "status", transaction.getStatus(),
                    "message", "Payment confirmed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Stripe payment confirmation failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Payment confirmation failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/storefront/bookings/{bookingId}/summary")
    public ResponseEntity<Map<String, Object>> getPaymentSummary(@PathVariable UUID bookingId) {
        UUID currentUser = AuthenticationUtils.getCurrentUserIdFromContext();

        List<Payment> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        if (payments.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Get the most recent completed payment, or most recent payment if none
        // completed
        Payment payment = payments.stream()
                .filter(p -> p.getStatus() == com.pdh.payment.model.enums.PaymentStatus.COMPLETED)
                .findFirst()
                .orElse(payments.get(0));

        if (payment.getUserId() != null && !payment.getUserId().equals(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("bookingId", payment.getBookingId());
        summary.put("paymentId", payment.getPaymentId());
        summary.put("amount", payment.getAmount());
        summary.put("currency", payment.getCurrency());
        summary.put("status", payment.getStatus());
        summary.put("updatedAt", payment.getUpdatedAt());

        // Add latest successful transaction ID for refund purposes
        payment.getTransactions().stream()
                .filter(t -> t.getStatus().isSuccessful())
                .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                .ifPresent(t -> summary.put("transactionId", t.getTransactionId()));

        return ResponseEntity.ok(summary);
    }

    /**
     * Process refund using Strategy Pattern
     */
    @PostMapping("/refund/{transactionId}")
    public ResponseEntity<Map<String, Object>> processRefund(
            @PathVariable UUID transactionId,
            @RequestBody RefundRequest refundRequest) {
        try {
            PaymentTransaction refundTransaction = paymentService.processRefund(
                    transactionId,
                    refundRequest.getAmount(),
                    refundRequest.getReason());

            Map<String, Object> response = Map.of(
                    "success", true,
                    "refundTransactionId", refundTransaction.getTransactionId(),
                    "status", refundTransaction.getStatus(),
                    "amount", refundTransaction.getAmount(),
                    "message", "Refund processed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Refund processing failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Refund processing failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Verify payment status using Strategy Pattern
     */
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<Map<String, Object>> verifyPaymentStatus(@PathVariable UUID transactionId) {
        try {
            PaymentTransaction transaction = paymentService.verifyPaymentStatus(transactionId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "transactionId", transaction.getTransactionId(),
                    "status", transaction.getStatus(),
                    "gatewayStatus", transaction.getGatewayStatus(),
                    "amount", transaction.getAmount(),
                    "currency", transaction.getCurrency(),
                    "message", "Status verified successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Payment status verification failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Status verification failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Cancel payment using Strategy Pattern
     */
    @PostMapping("/cancel/{transactionId}")
    public ResponseEntity<Map<String, Object>> cancelPayment(
            @PathVariable UUID transactionId,
            @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "User cancelled");
            PaymentTransaction cancelledTransaction = paymentService.cancelPayment(transactionId, reason);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "transactionId", cancelledTransaction.getTransactionId(),
                    "status", cancelledTransaction.getStatus(),
                    "message", "Payment cancelled successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Payment cancellation failed", e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "Payment cancellation failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Helper methods

    private Payment createPaymentFromRequest(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setPaymentReference(Payment.generatePaymentReference());
        payment.setBookingId(request.getBookingId());
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setDescription(request.getDescription());
        payment.setMethodType(request.getPaymentMethodType());
        payment.setProvider(PaymentProvider.STRIPE); // Default to Stripe for now
        payment.setSagaId(request.getSagaId());
        return payment;
    }

    private PaymentMethod createOrGetPaymentMethod(PaymentRequest request, UUID userId) {
        // This would typically check if payment method exists and create if not
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setUserId(userId);
        paymentMethod.setMethodType(request.getPaymentMethodType());
        paymentMethod.setProvider(PaymentProvider.STRIPE);
        paymentMethod.setDisplayName("Stripe Payment Method");
        paymentMethod.setIsActive(true);
        paymentMethod.setIsDefault(false);
        paymentMethod.setIsVerified(false);
        return paymentMethod;
    }
}

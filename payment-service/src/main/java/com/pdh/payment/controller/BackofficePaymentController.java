package com.pdh.payment.controller;

import com.pdh.common.dto.ApiResponse;
import com.pdh.payment.dto.*;
import com.pdh.payment.model.Payment;
import com.pdh.payment.model.PaymentTransaction;
import com.pdh.payment.model.enums.PaymentStatus;
import com.pdh.payment.service.BackofficePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backoffice Payment Controller
 * Handles admin payment management operations
 */
@RestController
@RequestMapping("/backoffice")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Backoffice Payment Management", description = "Admin payment operations")
public class BackofficePaymentController {

    private final BackofficePaymentService backofficePaymentService;

    /**
     * Get paginated list of payments with filters
     */
    @Operation(summary = "Get payments", description = "Get paginated list of payments with filtering")
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<Page<BackofficePaymentDto>>> getPayments(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Payment status") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Payment provider") @RequestParam(required = false) String provider,
            @Parameter(description = "Payment method type") @RequestParam(required = false) String methodType,
            @Parameter(description = "Booking ID") @RequestParam(required = false) UUID bookingId,
            @Parameter(description = "User ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Date from") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Date to") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Amount from") @RequestParam(required = false) BigDecimal amountFrom,
            @Parameter(description = "Amount to") @RequestParam(required = false) BigDecimal amountTo,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        log.info("Getting payments with filters - page: {}, size: {}, search: {}, status: {}", 
                page, size, search, status);

        try {
            PaymentFiltersDto filters = PaymentFiltersDto.builder()
                    .page(page)
                    .size(size)
                    .search(search)
                    .status(status)
                    .provider(provider)
                    .methodType(methodType)
                    .bookingId(bookingId)
                    .userId(userId)
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .amountFrom(amountFrom)
                    .amountTo(amountTo)
                    .sort(sort)
                    .direction(direction)
                    .build();

            Page<BackofficePaymentDto> payments = backofficePaymentService.getPayments(filters);
            return ResponseEntity.ok(ApiResponse.success(payments));

        } catch (Exception e) {
            log.error("Error getting payments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get payments", e.getMessage()));
        }
    }

    /**
     * Get payment by ID with full details
     */
    @Operation(summary = "Get payment details", description = "Get payment by ID with full details")
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<ApiResponse<Payment>> getPaymentById(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId) {

        log.info("Getting payment details for ID: {}", paymentId);

        try {
            Payment payment = backofficePaymentService.getPaymentById(paymentId);
            return ResponseEntity.ok(ApiResponse.success(payment));

        } catch (Exception e) {
            log.error("Error getting payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Payment not found", e.getMessage()));
        }
    }

    /**
     * Get payment transactions by payment ID
     */
    @Operation(summary = "Get payment transactions", description = "Get all transactions for a payment")
    @GetMapping("/payments/{paymentId}/transactions")
    public ResponseEntity<ApiResponse<List<PaymentTransaction>>> getPaymentTransactions(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId) {

        log.info("Getting transactions for payment: {}", paymentId);

        try {
            List<PaymentTransaction> transactions = backofficePaymentService.getPaymentTransactions(paymentId);
            return ResponseEntity.ok(ApiResponse.success(transactions));

        } catch (Exception e) {
            log.error("Error getting transactions for payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get transactions", e.getMessage()));
        }
    }

    /**
     * Get payment saga logs
     */
    @Operation(summary = "Get payment saga logs", description = "Get saga logs for a payment")
    @GetMapping("/payments/{paymentId}/saga-logs")

    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPaymentSagaLogs(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId) {

        log.info("Getting saga logs for payment: {}", paymentId);

        try {
            List<Map<String, Object>> sagaLogs = backofficePaymentService.getPaymentSagaLogs(paymentId);
            return ResponseEntity.ok(ApiResponse.success(sagaLogs));

        } catch (Exception e) {
            log.error("Error getting saga logs for payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get saga logs", e.getMessage()));
        }
    }

    /**
     * Reconcile payment with Stripe
     */
    @Operation(summary = "Reconcile payment with Stripe", description = "Check payment status with Stripe and sync if needed")
    @PostMapping("/payments/{paymentId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcilePayment(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId) {

        log.info("Reconciling payment: {}", paymentId);

        try {
            Map<String, Object> reconciliationResult = backofficePaymentService.reconcilePayment(paymentId);
            return ResponseEntity.ok(ApiResponse.success(reconciliationResult));

        } catch (IllegalArgumentException e) {
            log.error("Payment not found: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Payment not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Error reconciling payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reconcile payment", e.getMessage()));
        }
    }

    /**
     * Process manual payment (admin initiated)
     */
    @Operation(summary = "Process manual payment", description = "Process manual payment for admin")
    @PostMapping("/payments/manual")
    public ResponseEntity<ApiResponse<Payment>> processManualPayment(
            @Parameter(description = "Manual payment request", required = true) 
            @Valid @RequestBody ManualPaymentRequestDto request) {

        log.info("Processing manual payment for booking: {}", request.getBookingId());

        try {
            Payment payment = backofficePaymentService.processManualPayment(request);
            return ResponseEntity.ok(ApiResponse.success(payment));

        } catch (Exception e) {
            log.error("Error processing manual payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process manual payment", e.getMessage()));
        }
    }

    /**
     * Update payment status (admin action)
     */
    @Operation(summary = "Update payment status", description = "Update payment status with reason")
    @PutMapping("/payments/{paymentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Payment>> updatePaymentStatus(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "New status", required = true) @RequestParam PaymentStatus status,
            @Parameter(description = "Reason for status change") @RequestParam(required = false) String reason) {

        log.info("Updating payment {} status to {} with reason: {}", paymentId, status, reason);

        try {
            Payment payment = backofficePaymentService.updatePaymentStatus(paymentId, status, reason);
            return ResponseEntity.ok(ApiResponse.success(payment));

        } catch (Exception e) {
            log.error("Error updating payment status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update payment status", e.getMessage()));
        }
    }

    /**
     * Process refund
     */
    @Operation(summary = "Process refund", description = "Process refund for a payment")
    @PostMapping("/payments/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentTransaction>> processRefund(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Refund request", required = true) 
            @Valid @RequestBody RefundRequestDto request) {

        log.info("Processing refund for payment: {} amount: {}", paymentId, request.getAmount());

        try {
            PaymentTransaction refundTransaction = backofficePaymentService.processRefund(paymentId, request);
            return ResponseEntity.ok(ApiResponse.success(refundTransaction));

        } catch (Exception e) {
            log.error("Error processing refund for payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process refund", e.getMessage()));
        }
    }

    /**
     * Cancel payment
     */
    @Operation(summary = "Cancel payment", description = "Cancel a payment")
    @PutMapping("/payments/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<Payment>> cancelPayment(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Cancellation reason") @RequestParam(required = false) String reason) {

        log.info("Cancelling payment: {} with reason: {}", paymentId, reason);

        try {
            Payment payment = backofficePaymentService.cancelPayment(paymentId, reason);
            return ResponseEntity.ok(ApiResponse.success(payment));

        } catch (Exception e) {
            log.error("Error cancelling payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cancel payment", e.getMessage()));
        }
    }

    /**
     * Retry failed payment
     */
    @Operation(summary = "Retry payment", description = "Retry a failed payment")
    @PostMapping("/payments/{paymentId}/retry")
    public ResponseEntity<ApiResponse<Payment>> retryPayment(
            @Parameter(description = "Payment ID", required = true) @PathVariable UUID paymentId) {

        log.info("Retrying payment: {}", paymentId);

        try {
            Payment payment = backofficePaymentService.retryPayment(paymentId);
            return ResponseEntity.ok(ApiResponse.success(payment));

        } catch (Exception e) {
            log.error("Error retrying payment {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retry payment", e.getMessage()));
        }
    }

    /**
     * Get payment statistics
     */
    @Operation(summary = "Get payment statistics", description = "Get payment statistics for admin dashboard")
    @GetMapping("/payments/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStats(
            @Parameter(description = "Date from") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Date to") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Provider filter") @RequestParam(required = false) String provider) {

        log.info("Getting payment statistics for period: {} to {}, provider: {}", dateFrom, dateTo, provider);

        try {
            Map<String, Object> stats = backofficePaymentService.getPaymentStats(dateFrom, dateTo, provider);
            return ResponseEntity.ok(ApiResponse.success(stats));

        } catch (Exception e) {
            log.error("Error getting payment statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get payment statistics", e.getMessage()));
        }
    }

    /**
     * Get payments by booking ID
     */
    @Operation(summary = "Get payments by booking", description = "Get all payments for a booking")
    @GetMapping("/bookings/{bookingId}/payments")
    public ResponseEntity<ApiResponse<List<Payment>>> getPaymentsByBookingId(
            @Parameter(description = "Booking ID", required = true) @PathVariable UUID bookingId) {

        log.info("Getting payments for booking: {}", bookingId);

        try {
            List<Payment> payments = backofficePaymentService.getPaymentsByBookingId(bookingId);
            return ResponseEntity.ok(ApiResponse.success(payments));

        } catch (Exception e) {
            log.error("Error getting payments for booking {}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get payments for booking", e.getMessage()));
        }
    }

    /**
     * Export payments to CSV
     */
    @Operation(summary = "Export payments", description = "Export payments to CSV")
    @GetMapping("/payments/export")
    public void exportPayments(
            HttpServletResponse response,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Payment status") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Payment provider") @RequestParam(required = false) String provider,
            @Parameter(description = "Date from") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Date to") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        log.info("Exporting payments with filters - status: {}, provider: {}", status, provider);

        try {
            PaymentFiltersDto filters = PaymentFiltersDto.builder()
                    .search(search)
                    .status(status)
                    .provider(provider)
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .build();

            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"payments.csv\"");
            
            backofficePaymentService.exportPayments(filters, response.getOutputStream());

        } catch (IOException e) {
            log.error("Error exporting payments", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }


    /**
     * Get user payment methods
     */
    @Operation(summary = "Get user payment methods", description = "Get payment methods for a user")
    @GetMapping("/users/{userId}/payment-methods")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserPaymentMethods(
            @Parameter(description = "User ID", required = true) @PathVariable UUID userId) {

        log.info("Getting payment methods for user: {}", userId);

        try {
            List<Map<String, Object>> paymentMethods = backofficePaymentService.getUserPaymentMethods(userId);
            return ResponseEntity.ok(ApiResponse.success(paymentMethods));

        } catch (Exception e) {
            log.error("Error getting payment methods for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get user payment methods", e.getMessage()));
        }
    }

    /**
     * Get payment gateway webhooks
     */
    @Operation(summary = "Get payment webhooks", description = "Get payment gateway webhooks")
    @GetMapping("/webhooks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPaymentWebhooks(
            @Parameter(description = "Payment ID") @RequestParam(required = false) UUID paymentId,
            @Parameter(description = "Provider") @RequestParam(required = false) String provider,
            @Parameter(description = "Date from") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Date to") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        log.info("Getting payment webhooks - paymentId: {}, provider: {}", paymentId, provider);

        try {
            List<Map<String, Object>> webhooks = backofficePaymentService.getPaymentWebhooks(
                    paymentId, provider, dateFrom, dateTo);
            return ResponseEntity.ok(ApiResponse.success(webhooks));

        } catch (Exception e) {
            log.error("Error getting payment webhooks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get payment webhooks", e.getMessage()));
        }
    }
}
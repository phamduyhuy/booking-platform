package com.pdh.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for refund requests in backoffice
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDto {

    // Null amount means full refund
    @DecimalMin(value = "0.01", inclusive = false, message = "Refund amount must be greater than 0")
    private BigDecimal amount;

    private String reason;
}
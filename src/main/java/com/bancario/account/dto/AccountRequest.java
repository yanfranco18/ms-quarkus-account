package com.bancario.account.dto;

import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.CreditType;
import com.bancario.account.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccountRequest(
        @NotBlank(message = "El customerId no puede estar en blanco")
        String customerId,
        @NotNull(message = "El tipo de producto no puede ser nulo")
        ProductType productType,
        AccountType accountType,
        CreditType creditType,
        @NotNull(message = "El balance no puede ser nulo")
        @PositiveOrZero(message = "El balance inicial no puede ser negativo")
        BigDecimal balance,
        @NotNull(message = "El amountUsed no puede ser nulo")
        @PositiveOrZero(message = "El amountUsed inicial no puede ser negativo")
        BigDecimal amountUsed,
        LocalDateTime specificDepositDate,

        // Campos corregidos para titulares y firmantes
        List<String> holders,
        List<String> signatories
) {}
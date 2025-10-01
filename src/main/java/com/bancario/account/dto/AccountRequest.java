package com.bancario.account.dto;

import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.CreditType;
import com.bancario.account.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

        // El balance es esencial. La validación PositiveOrZero debe ir en el Service para TC/Líneas de crédito.
        @NotNull(message = "El balance no puede ser nulo")
        BigDecimal balance,

        LocalDateTime specificDepositDate,

        // Campos para cuentas empresariales
        List<String> holders,
        List<String> signatories
) {}
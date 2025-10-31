package com.bancario.account.dto;

import com.bancario.account.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccountResponse(
        // --- Identificación y Tipo ---
        String id,
        String customerId,
        String accountNumber,
        ProductType productType,
        AccountType accountType,
        CreditType creditType,
        AccountStatus status,
        LocalDateTime openingDate,

        // --- Estado Financiero ---
        BigDecimal balance, // Límite de crédito o saldo actual
        BigDecimal amountUsed, // Solo para productos ACTIVE (TC/Préstamos)

        // --- NUEVOS CAMPOS DE RIESGO ---
        Integer paymentDayOfMonth, // <-- AÑADIDO
        BigDecimal overdueAmount, // <-- AÑADIDO

        // --- Configuración y Monitoreo ---
        BigDecimal maintenanceFeeAmount, // Costo de mantenimiento
        BigDecimal requiredDailyAverage, // Para monitoreo VIP
        Integer freeTransactionLimit, // Límite de transacciones gratuitas
        BigDecimal transactionFeeAmount, // Costo por transacción excedida
        Integer currentMonthlyTransactions, // Contador actual de transacciones
        Integer monthlyMovements, // Opcional: Para cuentas de ahorro
        LocalDateTime specificDepositDate, // Opcional: Para plazo fijo

        // --- Titulares y Firmantes ---
        List<String> holders,
        List<String> signatories
) {}
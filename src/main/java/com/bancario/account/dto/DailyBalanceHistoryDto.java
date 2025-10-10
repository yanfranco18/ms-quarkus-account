package com.bancario.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para la transferencia de datos históricos de saldos/estados al final del día (EOD).
 * * Este record unifica la información de estado diario necesaria tanto para cuentas de depósito (PASSIVE)
 * como para productos de crédito (ACTIVE). Su principal objetivo es servir como fuente de datos
 * inmutable para el cálculo del Saldo Promedio Diario del cliente en el Report-Service.
 */
public record DailyBalanceHistoryDto(

        String productId,
        String accountType, // Necesita ser un String para la transferencia REST
        String productType, // CLAVE para la lógica: "PASSIVE" (Depósito) o "ACTIVE" (Crédito)
        LocalDate date,
        // Para Depósitos: Saldo EOD. Para Créditos: Línea Total.
        BigDecimal balanceEOD,
        // Para Créditos: Cantidad utilizada EOD. Para Depósitos: null o cero.
        BigDecimal amountUsedEOD
) {}
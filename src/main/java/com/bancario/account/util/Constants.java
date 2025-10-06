package com.bancario.account.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * Contiene todas las constantes de negocio para inicialización de cuentas y tarificación.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {

    // --- VALORES GENERALES (Punto Único para Cero) ---

    // Asignación de saldo inicial y valor cero para tarifas, promedios, etc.
    public static final BigDecimal INITIAL_BALANCE = BigDecimal.ZERO;

    // --- COMISIONES y LÍMITES POR DEFECTO (Cuentas Pasivas) ---

    // Comisión de mantenimiento mensual por defecto.
    public static final BigDecimal DEFAULT_MAINTENANCE_FEE = new BigDecimal("10.00");

    // Límite de transacciones gratuitas por defecto (Personal Estándar).
    public static final int DEFAULT_FREE_TXN_LIMIT = 4;

    // Comisión por transacción excedente por defecto.
    public static final BigDecimal DEFAULT_TXN_FEE_AMOUNT = new BigDecimal("0.50");

    // Valor inicial del contador de transacciones mensuales.
    public static final int INITIAL_MONTHLY_TRANSACTIONS = 0;

    // --- REGLAS VIP (SAVINGS_ACCOUNT) ---

    // Saldo promedio requerido para clientes VIP.
    public static final BigDecimal VIP_REQUIRED_AVERAGE = new BigDecimal("1000.00");

    // Límite de transacciones prácticamente ilimitado para VIP.
    public static final int VIP_FREE_TXN_LIMIT = 999;

    // --- REGLAS PYME (CURRENT_ACCOUNT) ---

    // Límite de transacciones gratuitas para PYME.
    public static final int PYME_FREE_TXN_LIMIT = 100;

    // Comisión por transacción baja para PYME.
    public static final BigDecimal PYME_TXN_FEE_AMOUNT = new BigDecimal("0.10");

    //updte contador 0
    public static final int UPDATE_COUNTER = 0;
}
package com.bancario.account.exception;

/**
 * Excepción de dominio que se lanza cuando un cliente no pasa la validación
 * de elegibilidad crediticia, típicamente debido a mora vencida (overdue debt).
 * También se utiliza como la excepción de seguridad (Fail-Safe) cuando el chequeo
 * asíncrono de riesgo falla (Circuit Breaker o Timeout activo).
 */
public class CreditEligibilityCheckFailedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor que acepta solo un mensaje de error.
     * @param message Mensaje descriptivo del error.
     */
    public CreditEligibilityCheckFailedException(String message) {
        super(message);
    }

    /**
     * Constructor que acepta un mensaje de error y la causa original (Throwable).
     * @param message Mensaje descriptivo del error.
     * @param cause La causa original del fallo, útil para fallbacks de FT.
     */
    public CreditEligibilityCheckFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
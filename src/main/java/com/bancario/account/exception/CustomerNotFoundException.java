package com.bancario.account.exception;

/**
 * Excepción lanzada cuando un cliente, identificado por su ID, no es encontrado
 * en el sistema. Extiende BusinessException para ser tratada como una violación
 * de regla de negocio o un error de recurso no encontrado (400 o 404).
 */
public class CustomerNotFoundException extends BusinessException {

    // Status HTTP sugerido: 404 Not Found.

    public CustomerNotFoundException(String customerId) {
        super("El cliente con ID '" + customerId + "' no fue encontrado.");
    }

    public CustomerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
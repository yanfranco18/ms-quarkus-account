package com.bancario.account.exception;

public class BusinessException extends RuntimeException {

    /**
     * Constructor que acepta un mensaje, ideal para errores de lógica de negocio.
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * Constructor opcional que acepta un mensaje y la causa original.
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
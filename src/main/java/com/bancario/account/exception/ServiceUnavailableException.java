package com.bancario.account.exception;

/**
 * Excepción lanzada cuando un servicio (o dependencia) no está disponible,
 * típicamente activada por un patrón de Resiliencia como Circuit Breaker o Fallback,
 * y mapeada a un código HTTP 503 (Service Unavailable).
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

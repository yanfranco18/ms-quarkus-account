package com.bancario.account.exception;

/**
 * Excepción de infraestructura lanzada para indicar un fallo en la capa de acceso a datos (persistencia).
 * * Esto incluye problemas de conexión, fallos de timeout, o errores genéricos del repositorio
 * no cubiertos por excepciones específicas de la base de datos (como MongoCommandException).
 * * Es una excepción no marcada (RuntimeException), por lo que el GlobalExceptionMapper
 * la capturará y mapeará a un 500 Internal Server Error.
 */
public class DataAccessException extends RuntimeException {

    // Se recomienda usar la causa raíz para no perder la traza original del error de persistencia.
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    // Constructor para mensajes simples, aunque se prefiere la versión con causa.
    public DataAccessException(String message) {
        super(message);
    }
}
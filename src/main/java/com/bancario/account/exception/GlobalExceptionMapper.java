package com.bancario.account.exception;

import com.mongodb.MongoCommandException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.LocalDateTime;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        int status;
        String error;

        switch (exception) {
            case CustomerNotFoundException customerNotFoundException -> {
                status = Response.Status.NOT_FOUND.getStatusCode(); // 404
                error = "Resource Not Found";
            }
            case ServiceUnavailableException serviceUnavailableException -> {
                // Excepción lanzada por el Fallback/Circuit Breaker
                status = Response.Status.SERVICE_UNAVAILABLE.getStatusCode(); // 503
                error = "Service Unavailable (Fault Tolerance)";
            }
            case BusinessException businessException -> {
                status = Response.Status.BAD_REQUEST.getStatusCode(); // 400
                error = "Violación de Regla de Negocio";
            }
            case IllegalArgumentException illegalArgumentException -> {
                status = Response.Status.BAD_REQUEST.getStatusCode(); // 400
                error = "Bad Request";
            }
            case MongoCommandException mongoCommandException -> {
                status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(); // 500
                error = "Database Error";
            }
            case null, default -> {
                status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(); // 500
                error = "Internal Server Error";
            }
        }

        ApiError apiError = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .message(exception.getMessage())
                .path(uriInfo.getPath()) // Usando UriInfo para obtener la ruta
                .build();

        return Response.status(status).entity(apiError).build();
    }
}
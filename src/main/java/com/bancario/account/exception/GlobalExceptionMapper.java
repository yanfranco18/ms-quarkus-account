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

        if (exception instanceof IllegalArgumentException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
            error = "Bad Request";
        } else if (exception instanceof MongoCommandException) {
            status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            error = "Database Error";
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            error = "Internal Server Error";
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
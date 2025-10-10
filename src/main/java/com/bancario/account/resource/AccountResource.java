package com.bancario.account.resource;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.dto.AccountTransactionStatus;
import com.bancario.account.dto.DailyBalanceHistoryDto;
import com.bancario.account.service.AccountService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Cuentas", description = "Endpoints para la gestión de cuentas bancarias.")
public class AccountResource {

    @Inject
    AccountService accountService;

    @POST
    @Operation(summary = "Crea una nueva cuenta bancaria o de crédito")
    @APIResponse(responseCode = "201", description = "Cuenta creada exitosamente",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccountResponse.class)))
    @APIResponse(responseCode = "400", description = "Solicitud inválida o reglas de negocio no cumplidas")
    public Uni<Response> createAccount(@RequestBody(required = true) AccountRequest request, @Context UriInfo uriInfo) {
        return accountService.crearCuenta(request)
                .onItem().transform(accountResponse -> {
                    URI uri = uriInfo.getAbsolutePathBuilder().path(accountResponse.id()).build();
                    return Response.created(uri).entity(accountResponse).build();
                });
    }

    @GET
    @Path("/{accountId}")
    @Operation(summary = "Busca una cuenta por su ID")
    @APIResponse(responseCode = "200", description = "Cuenta encontrada",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccountResponse.class)))
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada")
    public Uni<AccountResponse> getAccountById(@PathParam("accountId") String accountId) {
        return accountService.buscarPorCuentaId(accountId);
    }

    @GET
    @Operation(summary = "Busca todas las cuentas de un cliente por su ID")
    @APIResponse(responseCode = "200", description = "Lista de cuentas del cliente")
    public Multi<AccountResponse> getAccountsByCustomerId(@QueryParam("customerId") String customerId) {
        if (customerId == null || customerId.isEmpty()) {
            throw new BadRequestException("El parámetro 'customerId' es obligatorio.");
        }
        return accountService.findByCustomerId(customerId);
    }

    @DELETE
    @Path("/{accountId}")
    @Operation(summary = "Elimina una cuenta por su ID (cambia su estado a INACTIVO)")
    @APIResponse(responseCode = "204", description = "Cuenta eliminada exitosamente")
    @APIResponse(responseCode = "400", description = "No se puede eliminar la cuenta")
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada")
    public Uni<Response> deleteAccount(@PathParam("accountId") String accountId) {
        return accountService.eliminarCuenta(accountId)
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{accountId}/update-balance")
    @Operation(summary = "Updates the balance of an account.")
    @APIResponse(
            responseCode = "200",
            description = "Account balance updated successfully.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponse.class))
    )
    @APIResponse(responseCode = "400", description = "Invalid account ID or request body.")
    @APIResponse(responseCode = "404", description = "Account not found.")
    public Uni<Response> updateAccountBalance(@PathParam("accountId") String accountId, AccountResponse request) {
        return accountService.updateAccountBalance(accountId, request)
                .onItem().transform(account -> Response.ok(account).build())
                .onFailure().recoverWithItem(e -> {
                    if (e instanceof IllegalArgumentException) {
                        return Response.status(Response.Status.BAD_REQUEST).build();
                    } else if (e instanceof NoSuchElementException) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                });
    }

    /**
     * Endpoint consultado por el Transaction-Service para obtener límites y contador.
     */
    @GET
    @Path("/{accountId}/transaction-status")
    @Operation(summary = "Consulta la configuración de límites de transacciones y el contador mensual.",
            description = "Usado por el Transaction-Service para determinar si se debe aplicar comisión.")
    @APIResponse(responseCode = "200", description = "Devuelve el estado actual del contador, límite y monto de la tarifa.")
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada.")
    @APIResponse(responseCode = "400", description = "La cuenta no es de tipo transaccional (Pasivo).")
    public Uni<AccountTransactionStatus> getTransactionStatus(
            @PathParam(value = "accountId") String accountId) {

        return accountService.getAccountTransactionStatus(accountId);
    }

    /**
     * Endpoint llamado por el Transaction-Service para incrementar el contador de forma atómica.
     */
    @PATCH // PATCH es el verbo más adecuado para actualizar una porción del recurso (el contador).
    @Path("/{accountId}/increment-transactions")
    @Operation(summary = "Incrementa atómicamente el contador mensual de transacciones de la cuenta.",
            description = "Esta operación es atómica para garantizar la coherencia del contador bajo alta concurrencia.")
    @APIResponse(responseCode = "200", description = "Contador incrementado exitosamente.")
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada.")
    public Uni<Response> incrementTransactions(
            @PathParam(value = "accountId") String accountId) {
        // Llama al servicio, que ejecuta la lógica atómica del repositorio.
        return accountService.incrementMonthlyTransactionCounter(accountId)
                // Transforma el Uni<Void> retornado por el servicio en una respuesta HTTP 200 OK.
                .onItem().transform(ignored -> Response.ok().build());
    }

    @GET
    @Path("/by-number/{accountNumber}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Obtiene una cuenta por su número.",
            description = "Endpoint utilizado internamente por el Transaction-Service.")
    @APIResponse(responseCode = "200", description = "Cuenta encontrada.")
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada.")
    public Uni<AccountResponse> getAccountByNumber(@PathParam("accountNumber") String accountNumber) {
        log.info("Recibida solicitud GET /accounts/by-number/{}", accountNumber);
        return accountService.getAccountByNumber(accountNumber);
    }

    /**
     * Endpoint reactivo para obtener el historial de saldos diarios (EOD) de un cliente.
     * La gestión de excepciones (400, 500) se delega a un Global Exception Mapper.
     */
    @GET
    @Path("/daily-balances")
    @Operation(summary = "Obtiene el historial de saldos diarios (EOD) para un cliente.",
            description = "Utilizado para el cálculo analítico del Saldo Promedio Diario (SPD).")
    @APIResponse(responseCode = "200", description = "Lista de registros de saldo EOD.")
    @APIResponse(responseCode = "400", description = "Parámetros inválidos (Manejado por Exception Mapper).")
    @APIResponse(responseCode = "500", description = "Fallo interno (Manejado por Exception Mapper).")
    public Uni<List<DailyBalanceHistoryDto>> getDailyBalances(
            @Parameter(description = "ID único del cliente.")
            @QueryParam("customerId")
            String customerId,
            @Parameter(description = "Fecha de inicio (YYYY-MM-DD).")
            @QueryParam("startDate")
            LocalDate startDate,
            @Parameter(description = "Fecha de fin (YYYY-MM-DD).")
            @QueryParam("endDate")
            LocalDate endDate
    ) {
        log.info("SPD Resource: Solicitud recibida para customerId: {}", customerId);
        return accountService.getDailyBalancesByCustomer(customerId, startDate, endDate)
                // 2. Logging y retorno directo del item (la lista de DTOs).
                // Mutiny se encarga de envolver el List<DTO> en la respuesta 200 OK.
                .onItem().invoke(data -> {
                    log.debug("SPD Resource: Retornando {} registros. Delegando la respuesta final.", data.size());
                });
    }
}
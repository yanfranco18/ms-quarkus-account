package com.bancario.account.resource;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.service.AccountService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;

@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
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
}
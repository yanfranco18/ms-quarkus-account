package com.bancario.account.service;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;

/**
 * Interfaz de servicio para la gestión de productos bancarios.
 * Define los contratos para la creación, cancelación y búsqueda de cuentas.
 */
public interface AccountService {

    /**
     * Crea un nuevo producto bancario (cuenta pasiva o activa) según la solicitud.
     * @param request Datos de la solicitud para crear la cuenta.
     * @return Un objeto Uni que emite la respuesta de la cuenta creada.
     */
    Uni<AccountResponse> crearCuenta(AccountRequest request);

    /**
     * Busca un producto bancario por su identificador de cuenta.
     * @param accountId El ID único de la cuenta.
     * @return Un objeto Uni que emite la respuesta de la cuenta encontrada.
     */
    Uni<AccountResponse> buscarPorCuentaId(String accountId);

    /**
     * Elimina (cancela) un producto bancario.
     * @param accountId El ID de la cuenta a cancelar.
     * @return Un objeto Uni que indica la finalización de la operación.
     */
    Uni<Void> eliminarCuenta(String accountId);

    /**
     * Busca todas las cuentas de un cliente por su ID de cliente.
     * @param customerId El ID del cliente.
     * @return Un objeto Multi que emite las cuentas del cliente.
     */
    Multi<AccountResponse> findByCustomerId(String customerId);

    /**
     * Updates the balance of an account.
     *
     * @param accountId The ID of the account to be updated.
     * @param updatedAccount The account data transfer object (DTO) containing the new balance.
     * @return A Uni that emits the updated account.
     */
    Uni<AccountResponse> updateAccountBalance(String accountId, AccountResponse updatedAccount);
}

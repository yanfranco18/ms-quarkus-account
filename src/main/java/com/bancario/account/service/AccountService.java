package com.bancario.account.service;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.dto.AccountTransactionStatus;
import com.bancario.account.dto.DailyBalanceHistoryDto;
import com.bancario.account.exception.CustomerNotFoundException;
import com.bancario.account.exception.DataAccessException;
import com.bancario.account.repository.entity.Account;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * Recupera la configuración de tarifas y el estado actual de transacciones
     * (límites y contador) para que el Transaction-Service pueda aplicar la regla de tarificación.
     */
    Uni<AccountTransactionStatus> getAccountTransactionStatus(String accountId);

    /**
     * Incrementa atómicamente el contador mensual de transacciones de la cuenta.
     * Llamado por el Transaction-Service después de cada depósito/retiro exitoso.
     * Retorna Uni<Void> ya que al Transaction-Service solo le importa que la operación se complete.
     */
    Uni<Void> incrementMonthlyTransactionCounter(String accountId);

    /**
     * Obtiene una cuenta por su número de cuenta.
     */
    Uni<AccountResponse> getAccountByNumber(String accountNumber);

    /**
     * Obtiene el historial de saldos/estados al final del día (EOD) para todos los productos
     * (cuentas de depósito y créditos) que posee un cliente, dentro de un rango de fechas.
     * * Este método es vital para cálculos analíticos como el Saldo Promedio Diario (SPD),
     * cumpliendo con el Principio de Segregación de Interfaces (ISP) al agrupar una funcionalidad
     * específica de consulta histórica.
     *
     * @param customerId El ID único del cliente para quien se solicitan los saldos.
     * @param startDate La fecha de inicio del periodo de consulta (inclusiva).
     * @param endDate La fecha de fin del periodo de consulta (inclusiva).
     * @return Uni que emite una lista inmutable de DailyBalanceHistoryDto con los saldos diarios.
     * @throws CustomerNotFoundException Si no se encuentra el cliente especificado.
     * @throws DataAccessException Si ocurre un problema al acceder a la capa de persistencia.
     */
    Uni<List<DailyBalanceHistoryDto>> getDailyBalancesByCustomer(
            String customerId,
            LocalDate startDate,
            LocalDate endDate
    ) throws CustomerNotFoundException, DataAccessException;
}

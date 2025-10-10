package com.bancario.account.service.impl;

import com.bancario.account.client.CustomerServiceRestClient;
import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.dto.AccountTransactionStatus;
import com.bancario.account.dto.DailyBalanceHistoryDto;
import com.bancario.account.enums.AccountStatus;
import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.ProductType;
import com.bancario.account.enums.CustomerType;
import com.bancario.account.exception.BusinessException;
import com.bancario.account.exception.CustomerNotFoundException;
import com.bancario.account.exception.DataAccessException;
import com.bancario.account.exception.ServiceUnavailableException;
import com.bancario.account.mapper.AccountMapper;
import com.bancario.account.mapper.BalanceSnapshotMapper;
import com.bancario.account.repository.AccountRepository;
import com.bancario.account.repository.BalanceSnapshotRepository;
import com.bancario.account.repository.entity.Account;
import com.bancario.account.repository.entity.BalanceSnapshot;
import com.bancario.account.service.AccountService;
import com.bancario.account.util.Constants;
import io.quarkus.mongodb.panache.common.reactive.ReactivePanacheUpdate;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

@Slf4j
@ApplicationScoped
public class AccountServiceImpl implements AccountService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    BalanceSnapshotRepository snapshotRepository;

    @Inject
    AccountMapper accountMapper;

    @Inject
    BalanceSnapshotMapper snapshotMapper;

    @Inject
    @RestClient
    CustomerServiceRestClient customerServiceRestClient;

    @Override
    public Uni<AccountResponse> crearCuenta(AccountRequest request) {
        log.info("Creating a new account for customer with ID: {}", request.customerId());

        // 1. Realiza las validaciones síncronas primero.
        try {
            validateSynchronousAccountCreation(request);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().failure(e);
        }

        // 2. Encadena las validaciones asíncronas de forma condicional.
        Uni<Void> validationUni;
        if (request.productType() == ProductType.ACTIVE) {
            validationUni = validateActiveAccountCreation(request);
        } else if (request.productType() == ProductType.PASSIVE) {
            validationUni = validatePassiveAccountCreation(request);
        } else {
            // Si no es un tipo de producto válido, lanza una excepción o devuelve un Uni vacío.
            return Uni.createFrom().failure(new IllegalArgumentException("Invalid product type."));
        }
        return validationUni.chain(() -> assignSpecialAttributesAndPersist(request));
    }

    @Override
    @Timeout // Lee 1000ms del properties
    @CircuitBreaker // Lee requestVolumeThreshold, failureRatio, delay, successThreshold
    @Fallback(fallbackMethod = "fallbackBuscarPorCuentaId")
    public Uni<AccountResponse> buscarPorCuentaId(String accountId) {
        log.info("Finding account with ID: {}", accountId);

        return accountRepository.findById(toObjectId(accountId))
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Account not found with ID: " + accountId))
                .onItem().transform(account -> accountMapper.toResponse(account));
    }

    @Override
    public Uni<Void> eliminarCuenta(String accountId) {
        log.info("Eliminating account with ID: {}", accountId);

        return accountRepository.findById(toObjectId(accountId))
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Account not found with ID: " + accountId))
                .onItem().invoke(this::validateAccountStatusChange)
                .onItem().transformToUni(account -> accountRepository.update(account))
                .onItem().ignore().andContinueWithNull();
    }

    @Override
    public Multi<AccountResponse> findByCustomerId(String customerId) {
        log.info("Finding all accounts for customer with ID: {}", customerId);

        return accountRepository.find("customerId", customerId)
                .stream()
                .onItem().transform(account -> accountMapper.toResponse(account));
    }

    @Override
    public Uni<AccountResponse> updateAccountBalance(String accountId, AccountResponse updatedAccount) {
        return accountRepository.findById(new ObjectId(accountId))
                .onItem().ifNull().failWith(() -> new NoSuchElementException("Account with ID " + accountId + " not found."))
                .onItem().transformToUni(account -> {
                    // Actualiza el balance o el amountUsed
                    account.setBalance(updatedAccount.balance());
                    account.setAmountUsed(updatedAccount.amountUsed());

                    return accountRepository.persistOrUpdate(account)
                            .chain(() -> {
                                // 2. Después de la persistencia exitosa, transformar la entidad a una respuesta.
                                return Uni.createFrom().item(accountMapper.toResponse(account));
                            });
                });
    }

    @Override
    public Uni<AccountTransactionStatus> getAccountTransactionStatus(String accountId) {
        return accountRepository.findById(new ObjectId(accountId))
                .onItem().ifNotNull().transform(account -> {
                    // 1. Validación de Producto: La regla aplica a cuentas pasivas.
                    if (account.productType != ProductType.PASSIVE) {
                        throw new BusinessException("Solo las cuentas pasivas (Ahorro/Corriente) tienen límites de transacciones.");
                    }
                    // 2. Mapeo al DTO de estado de transacción
                    return new AccountTransactionStatus(
                            account.freeTransactionLimit,
                            account.currentMonthlyTransactions,
                            account.transactionFeeAmount
                    );
                })
                .onItem().ifNull().failWith(() -> new NotFoundException("Cuenta con ID " + accountId + " no encontrada."));
    }

    /**
     * Obtiene una cuenta por su número de cuenta.
     * @param accountNumber El número de cuenta a buscar.
     * @return Uni<Account> El objeto Account.
     */
    public Uni<AccountResponse> getAccountByNumber(String accountNumber) {
        log.info("Buscando cuenta por número: {}", accountNumber);
        return accountRepository.findByAccountNumber(accountNumber)
                .onItem().ifNull().failWith(() -> {
                    log.warn("Cuenta con número {} no encontrada.", accountNumber);
                    return new NotFoundException("Account not found with number: " + accountNumber);
                })
                .onItem().transform(account -> accountMapper.toResponse(account));
    }

    /**
     * Obtiene el historial de saldos/estados al final del día (EOD) para todos los productos
     * (cuentas de depósito y créditos) que posee un cliente, dentro de un rango de fechas.
     * * Este método orquesta la llamada al repositorio de snapshots, maneja la validación de la entrada,
     * transforma los errores de persistencia en excepciones de la capa de negocio,
     * y mapea las entidades BalanceSnapshot al DTO DailyBalanceHistoryDto para su transferencia
     * usando el BalanceSnapshotMapper dedicado.
     *
     * @param customerId El ID único del cliente para quien se solicitan los saldos.
     * @param startDate La fecha de inicio del periodo de consulta (inclusiva).
     * @param endDate La fecha de fin del periodo de consulta (inclusiva).
     * @return Uni que emite una lista inmutable de DailyBalanceHistoryDto con los saldos diarios.
     * @throws CustomerNotFoundException Esta excepción puede ser lanzada si el cliente no es válido,
     * aunque se prioriza devolver una lista vacía si no hay historial para el periodo.
     * @throws DataAccessException Si ocurre un error irrecuperable al acceder a la capa de persistencia (ej. timeout de BD).
     * @throws IllegalArgumentException Si el customerId es nulo o vacío.
     */
    @Override
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackDailyBalances")
    public Uni<List<DailyBalanceHistoryDto>> getDailyBalancesByCustomer(
            String customerId,
            LocalDate startDate,
            LocalDate endDate
    ) throws CustomerNotFoundException, DataAccessException {
        log.info("SPD Consulta EOD para customerId: {}, rango: [{} - {}]",
                customerId, startDate, endDate);

        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Se intentó consultar saldos con customerId inválido.");
            return Uni.createFrom().failure(new IllegalArgumentException("El ID de cliente es obligatorio."));
        }
        // 2. Orquestación y Flujo Reactivo
        return snapshotRepository.findByCustomerAndDateRange(customerId, startDate, endDate)
                // 3. Manejo de Fallos de Persistencia
                // Usamos invoke para registrar el error y transform para lanzar la excepción de negocio
                .onFailure().invoke(failure -> {
                    // Log del error real del repositorio
                    log.error("Fallo crítico al acceder al historial de saldos para customerId: {}. Causa: {}",
                            customerId, failure.getMessage(), failure);
                })
                // Si hay un fallo, transformamos CUALQUIER fallo en nuestra DataAccessException
                .onFailure().transform(failure -> new DataAccessException("Fallo al recuperar saldos diarios históricos.", failure))
                // 4. Mapeo del Resultado (onItem().map)
                .onItem().transform(snapshots -> {
                    // Si la lista está vacía, retornamos lista vacía.
                    if (snapshots.isEmpty()) {
                        log.warn("No se encontraron snapshots de saldo en el rango solicitado para customerId: {}", customerId);
                        return List.of();
                    }
                    // Mapeo de Entidad (BalanceSnapshot) a DTO (DailyBalanceHistoryDto)
                    log.debug("Mapeando {} snapshots recuperados a DTOs para customerId: {}",
                            snapshots.size(), customerId);

                    return snapshotMapper.toDtoList(snapshots);
                });
    }

    @Override
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackIncrementCounter")
    public Uni<Void> incrementMonthlyTransactionCounter(String accountId) {
        // Llama al repositorio, que usa el comando atómico y devuelve Uni<Long> (el conteo).
        return accountRepository.incrementMonthlyTransactionCounter(accountId)
                // Usamos transformToUni para inspeccionar el resultado del conteo
                .onItem().transformToUni(updatedCount -> {
                    // Si el conteo es 0, significa que la cuenta no se encontró para actualizar.
                    if (updatedCount == Constants.UPDATE_COUNTER) {
                        // Lanzar una excepción que el Controller mapeará a HTTP 404
                        return Uni.createFrom().failure(new NotFoundException("Account not found with ID: " + accountId));
                    }
                    // Si el conteo es > 0 (normalmente 1), la operación fue exitosa.
                    // Retornamos Uni<Void> como lo requiere el contrato del Service.
                    return Uni.createFrom().voidItem();
                })
                // Opcional: Manejo de errores de base de datos.
                .onFailure().invoke(e -> log.error("Error al incrementar el contador atómico: {}", e.getMessage()));
    }

    // Método para la validación de cuentas activas (créditos)
    private Uni<Void> validateActiveAccountCreation(AccountRequest request) {
        return customerServiceRestClient.getCustomerById(request.customerId())
                .onItem().transformToUni(customerResponse -> {
                    if (customerResponse.type() == CustomerType.PERSONAL) {
                        return accountRepository.countActiveProducts(request.customerId())
                                .onItem().transformToUni(count -> {
                                    if (count >= 1) {
                                        return Uni.createFrom().failure(new IllegalArgumentException("A personal customer cannot have more than one active credit."));
                                    }
                                    return Uni.createFrom().voidItem();
                                });
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<Void> validatePassiveAccountCreation(AccountRequest request) {
        return customerServiceRestClient.getCustomerById(request.customerId())
                .onItem().transformToUni(customerResponse -> {

                    CustomerType customerType = customerResponse.type();

                    // --- VALIDACIÓN DE PERFILES ESPECIALES (VIP / PYME) ---
                    if (customerType == CustomerType.VIP) {
                        // El VIP es un tipo de cliente PERSONAL.
                        return validateVipEligibility(request);
                    }

                    if (customerType == CustomerType.PYME) {
                        // El PYME es un tipo de cliente EMPRESARIAL.
                        return validatePymeEligibility(request);
                    }

                    if (customerResponse.type() == CustomerType.PERSONAL) {
                        return accountRepository.countAccountsByType(request.customerId(), request.accountType())
                                .onItem().transformToUni(count -> {
                                    if (count >= 1) {
                                        return Uni.createFrom().failure(new IllegalArgumentException("A personal customer can only have one " + request.accountType() + " account."));
                                    }
                                    return Uni.createFrom().voidItem();
                                });
                    }

                    if (customerResponse.type() == CustomerType.EMPRESARIAL) {
                        // Validación corregida para la lista de titulares.
                        if (request.holders() == null || request.holders().isEmpty()) {
                            return Uni.createFrom().failure(new IllegalArgumentException("A business account must have at least one holder."));
                        }
                        // Un cliente empresarial no puede tener una cuenta de ahorro o de plazo fijo, pero sí múltiples cuentas corrientes.
                        if (request.accountType() == AccountType.SAVINGS_ACCOUNT || request.accountType() == AccountType.FIXED_TERM_DEPOSIT) {
                            return Uni.createFrom().failure(new IllegalArgumentException("A business customer cannot have savings or fixed-term deposit accounts."));
                        }
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    // Método para validaciones síncronas de la solicitud
    private void validateSynchronousAccountCreation(AccountRequest request) {

        if (request.balance() == null) {
            throw new IllegalArgumentException("Initial balance cannot be null.");
        }
        // 2. VALIDACIÓN DE REGLAS DE CUENTAS BANCARIAS PASIVAS
        // Aplicamos la regla de "balance >= $0" solo a los tipos de cuenta requeridos.
        if (isBankTypeAccount(request.accountType())) {

            // **REQUERIMIENTO IMPLEMENTADO:** Balance debe ser cero o mayor ($0.00 o más).
            if (request.balance().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Bank accounts (Savings, Current, Fixed Term) require an initial balance of zero or greater.");
            }

            // 3. Manejo de tipos específicos dentro de las cuentas pasivas
            if (request.productType() == ProductType.PASSIVE) {
                 if (request.accountType() == AccountType.FIXED_TERM_DEPOSIT) {
                    if (request.specificDepositDate() == null) {
                        throw new IllegalArgumentException("Fixed-term deposits require a specific deposit date.");
                    }
                }
            }
        }
    }

    // Método para convertir String a ObjectId
    private ObjectId toObjectId(String id) {
        return new ObjectId(id);
    }

    private void validateAccountStatusChange(Account account) {
        if (account.getProductType() == ProductType.PASSIVE) {
            // Regla para cuentas pasivas (ahorro, corriente): el saldo debe ser cero.
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Cannot eliminate a passive account with a non-zero balance.");
            }
        } else if (account.getProductType() == ProductType.ACTIVE) {
            // Regla para cuentas activas (créditos): el monto consumido debe ser cero.
            if (account.getAmountUsed().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Cannot eliminate an active account with a non-zero amount used.");
            }
        }
    }

    /**
     * Verifica si el tipo de cuenta está sujeto a la regla de "balance de apertura >= 0"
     * (Ahorro, Corriente, Plazo Fijo).
     */
    private boolean isBankTypeAccount(AccountType type) {
        if (type == null) {
            return false;
        }
        return type == AccountType.SAVINGS_ACCOUNT ||
                type == AccountType.CURRENT_ACCOUNT ||
                type == AccountType.FIXED_TERM_DEPOSIT;
    }

    private Uni<Void> validateVipEligibility(AccountRequest request) {
        // El tipo de cuenta que se está creando.
        AccountType requestedType = request.accountType();
        String customerId = request.customerId();

        // --- LÓGICA PARA CUENTAS NO-AHORRO (Hereda regla de Unicidad de PERSONAL) ---
        if (requestedType != AccountType.SAVINGS_ACCOUNT) {

            // REGLA: Un cliente VIP (Personal) solo puede tener UNA cuenta de cada tipo (Corriente, Plazo Fijo, etc.).
            // Sustituido: find("customerId = ?1 and accountType = ?2", ...).count()
            return accountRepository.countAccountsByType(customerId, requestedType)
                    .onItem().transformToUni(count -> {
                        if (count >= 1) {
                            return Uni.createFrom().failure(new IllegalArgumentException(
                                    "A VIP customer can only have one " + requestedType + " account (excluding Savings)."
                            ));
                        }
                        return Uni.createFrom().voidItem();
                    });
        }

        // --- LÓGICA PARA CUENTA DE AHORRO VIP (Reglas Especiales) ---
        // REGLA 1: Debe tener una Tarjeta de Crédito activa (ASÍNCRONO - LÓGICA LOCAL)
        return accountRepository.hasActiveCreditCard(customerId)
                .onItem().transformToUni(hasCreditCard -> {
                    if (Boolean.FALSE.equals(hasCreditCard)) {
                        return Uni.createFrom().failure(new IllegalArgumentException(
                                "El cliente VIP debe tener una tarjeta de crédito activa para abrir una Cuenta de Ahorro VIP."
                        ));
                    }

                    // REGLA 2: No debe tener ya una cuenta de ahorro VIP.
                    // Sustituido: find("customerId = ?1 and accountType = ?2", ...).count()
                    return accountRepository.countAccountsByType(customerId, requestedType) // <-- USO CLEAN CODE
                            .onItem().transformToUni(count -> {
                                if (count >= 1) {
                                    return Uni.createFrom().failure(new IllegalArgumentException("Un cliente VIP solo puede tener una cuenta de ahorro VIP."));
                                }
                                return Uni.createFrom().voidItem();
                            });
                });
    }

    private Uni<Void> validatePymeEligibility(AccountRequest request) {

        // REGLA CLAVE 1: El perfil PYME solo aplica sus reglas a la CUENTA CORRIENTE.
        if (request.accountType() != AccountType.CURRENT_ACCOUNT) {
            // Un cliente PYME no puede tener cuentas de Ahorro o Plazo Fijo.
            if (request.accountType() == AccountType.SAVINGS_ACCOUNT || request.accountType() == AccountType.FIXED_TERM_DEPOSIT) {
                return Uni.createFrom().failure(new IllegalArgumentException("A PYME customer cannot have savings or fixed-term deposit accounts."));
            }
            return Uni.createFrom().voidItem();
        }

        // REGLA CLAVE 2: Debe tener una Tarjeta de Crédito activa (ASÍNCRONO - LÓGICA LOCAL)
        return accountRepository.hasActiveCreditCard(request.customerId())
                .onItem().transformToUni(hasCreditCard -> {
                    if (Boolean.FALSE.equals(hasCreditCard)) {
                        return Uni.createFrom().failure(new IllegalArgumentException(
                                "El cliente PYME debe tener una tarjeta de crédito activa para abrir una Cuenta Corriente PYME."
                        ));
                    }

                    // REGLA 3 (Heredada): Debe tener al menos un titular.
                    if (request.holders() == null || request.holders().isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("A business account must have at least one holder."));
                    }

                    return Uni.createFrom().voidItem();
                });
    }

    // --- Nuevo Método: Asignar Atributos y Persistir ---
    private Uni<AccountResponse> assignSpecialAttributesAndPersist(AccountRequest request) {
        return customerServiceRestClient.getCustomerById(request.customerId())
                .onItem().transformToUni(customerResponse -> {

                    Account newAccount = accountMapper.toEntity(request);
                    CustomerType customerType = customerResponse.type();

                    // --- LÓGICA DE INICIALIZACIÓN BÁSICA ---
                    newAccount.setAccountNumber(
                            generateAccountNumber(request.productType(), request.accountType())
                    );
                    newAccount.setOpeningDate(LocalDateTime.now());
                    newAccount.setStatus(AccountStatus.ACTIVE);
                    // 1. INICIALIZACIÓN DE COMISIÓN DE MANTENIMIENTO (DEFAULT)
                    newAccount.maintenanceFeeAmount = Constants.DEFAULT_MAINTENANCE_FEE;
                    newAccount.requiredDailyAverage = Constants.INITIAL_BALANCE;

                    // 2. INICIALIZACIÓN DE LÍMITES DE TRANSACCIÓN (DEFAULT: PERSONAL/EMPRESARIAL)
                    if (request.productType() == ProductType.PASSIVE) {
                        newAccount.freeTransactionLimit = Constants.DEFAULT_FREE_TXN_LIMIT; // 4 transacciones gratuitas por defecto
                        newAccount.transactionFeeAmount = Constants.DEFAULT_TXN_FEE_AMOUNT; // Comisión de $0.50 por excedente
                        newAccount.currentMonthlyTransactions = Constants.INITIAL_MONTHLY_TRANSACTIONS; // Contador inicia en cero
                    } else {
                        // Para productos ACTIVE (TC/Préstamos), estos campos no aplican
                        newAccount.freeTransactionLimit = null;
                        newAccount.transactionFeeAmount = null;
                        newAccount.currentMonthlyTransactions = null;
                    }

                    // LÓGICA DE ASIGNACIÓN VIP (Ahorro)
                    if (customerResponse.type() == CustomerType.VIP &&
                            request.accountType() == AccountType.SAVINGS_ACCOUNT) {

                        // Requisito de Monitoreo
                        newAccount.requiredDailyAverage = Constants.VIP_REQUIRED_AVERAGE;
                        // Comisión Cero (sin comisión)
                        newAccount.maintenanceFeeAmount = Constants.INITIAL_BALANCE;
                        newAccount.freeTransactionLimit = Constants.VIP_FREE_TXN_LIMIT; // Prácticamente ilimitado
                        newAccount.transactionFeeAmount = Constants.INITIAL_BALANCE; // Comisión de transacción Cero
                        log.info("Assigned VIP attributes to account ID {}: Avg. ${}, Fee ${}, Txn Limit {}",
                                newAccount.id, Constants.VIP_REQUIRED_AVERAGE, Constants.INITIAL_BALANCE, Constants.VIP_FREE_TXN_LIMIT);
                    }
                    // LÓGICA DE ASIGNACIÓN PYME (Cuenta Corriente)
                    else if (customerResponse.type() == CustomerType.PYME &&
                            request.accountType() == AccountType.CURRENT_ACCOUNT) {
                        // Regla Especial PYME: Limite moderado con comisión baja
                        newAccount.freeTransactionLimit = Constants.PYME_FREE_TXN_LIMIT; // Límite más alto que el estándar
                        newAccount.transactionFeeAmount = Constants.PYME_TXN_FEE_AMOUNT; // Comisión baja por excedente

                        log.info("Assigned PYME attributes to account ID {}: Txn Limit {}, Txn Fee ${}",
                                newAccount.id, Constants.PYME_FREE_TXN_LIMIT, Constants.PYME_TXN_FEE_AMOUNT);
                    }
                    return accountRepository.persist(newAccount)
                            .onItem().transform(accountMapper::toResponse);
                });
    }

    /**
     * Genera un número de cuenta único con un prefijo basado en el tipo de producto
     * (Activo/Pasivo) y el tipo de cuenta específico (Ahorro, Corriente, Plazo Fijo).
     *
     * Los prefijos bancarios son:
     * - 0300-: Para todas las cuentas de Crédito (ACTIVE).
     * - 0200-: Cuenta de Ahorro (SAVINGS_ACCOUNT).
     * - 0201-: Cuenta Corriente (CURRENT_ACCOUNT).
     * - 0202-: Plazo Fijo (FIXED_TERM_DEPOSIT).
     *
     * @param productType El tipo de producto (ACTIVE/PASSIVE) de la nueva cuenta.
     * @param accountType El tipo de cuenta específico (e.g., SAVINGS_ACCOUNT).
     * @return String El número de cuenta generado con el formato [PREFIJO]-[8 dígitos aleatorios].
     */
    private String generateAccountNumber(ProductType productType, AccountType accountType) {
        String prefix;
        log.info("Generating account number for ProductType: {} and AccountType: {}", productType, accountType);

        // 1. DETERMINAR EL PREFIJO BASADO EN LA CATEGORÍA DEL PRODUCTO
        if (productType == ProductType.ACTIVE) {
            // Regla de Negocio: Todas las cuentas de CRÉDITO (ACTIVE) usan el mismo prefijo.
            prefix = "0300-";
            log.debug("Assigned prefix 0300- for ACTIVE product.");

        } else { // ProductType.PASSIVE
            // 2. DETERMINAR EL PREFIJO PARA CUENTAS PASIVAS
            switch (accountType) {
                case SAVINGS_ACCOUNT:
                    // Cuentas de Ahorro: 0200-
                    prefix = "0200-";
                    log.debug("Assigned prefix 0200- for SAVINGS_ACCOUNT.");
                    break;
                case CURRENT_ACCOUNT:
                    // Cuentas Corrientes: 0201-
                    prefix = "0201-";
                    log.debug("Assigned prefix 0201- for CURRENT_ACCOUNT.");
                    break;
                case FIXED_TERM_DEPOSIT:
                    // Cuentas de Plazo Fijo: 0202-
                    prefix = "0202-";
                    log.debug("Assigned prefix 0202- for FIXED_TERM_DEPOSIT.");
                    break;
                default:
                    // Fallback: Si se recibe un tipo de cuenta pasiva no especificado.
                    prefix = "0299-";
                    log.warn("Using fallback prefix 0299- for unknown PASSIVE AccountType: {}", accountType);
                    break;
            }
        }
        // 3. GENERAR LA PARTE ALEATORIA DEL NÚMERO DE CUENTA
        // Generamos una parte aleatoria de 8 dígitos para garantizar unicidad.
        // El cálculo produce un número entero aleatorio entre 10,000,000 y 99,999,999.
        long randomPart = (long) (Math.random() * 90_000_000L) + 10_000_000L;
        String generatedNumber = prefix + String.valueOf(randomPart);

        log.info("Generated final account number: {}", generatedNumber);
        // 4. RETORNAR EL NÚMERO DE CUENTA COMPLETO
        return generatedNumber;
    }

    /**
     * Método de contingencia (Fallback) para la consulta de cuentas por ID.
     * Se activa si el método original falla por Timeout o Circuit Breaker abierto.
     * @param accountId El ID de la cuenta que causó el fallo.
     * @param failure La causa de la falla.
     * @return Uni<AccountResponse> que lanza una excepción de servicio no disponible.
     */
    public Uni<AccountResponse> fallbackBuscarPorCuentaId(String accountId, Throwable failure) {
        log.error("FALLBACK ACTIVO en buscarPorCuentaId para ID {}. Causa: {}", accountId, failure.getMessage());

        String errorMessage = "El servicio de cuentas está temporalmente no disponible (Fallback activo).";

        // Lanzamos la excepción para que sea mapeada a HTTP 503 por el GlobalExceptionMapper
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage));
    }

    /**
     * Provee una excepción de servicio no disponible cuando falla la operación crítica.
     */
    public Uni<Void> fallbackIncrementCounter(String accountId, Throwable failure) {
        log.error("FALLBACK ACTIVO para incremento de contador de cuenta {}. Causa: {}", accountId, failure.getMessage());

        // Para operaciones de escritura críticas, la mejor opción es fallar con un error explícito.
        String errorMessage = String.format("El servicio de actualización de contadores está inoperativo. Causa: %s", failure.getMessage());
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }

    /**
     * Método de Fallback para getDailyBalancesByCustomer.
     */
    public Uni<List<DailyBalanceHistoryDto>> fallbackDailyBalances(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            Throwable failure
    ) {
        log.error("FALLBACK ACTIVO (Saldos Diarios) para cliente {}. Causa: {}", customerId, failure.getMessage());

        String errorMessage = String.format("El servicio de historial de saldos está inoperativo. Causa: %s", failure.getMessage());
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }

}
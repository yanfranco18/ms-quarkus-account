package com.bancario.account.service.impl;

import com.bancario.account.client.CustomerServiceRestClient;
import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.dto.AccountTransactionStatus;
import com.bancario.account.enums.AccountStatus;
import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.ProductType;
import com.bancario.account.enums.CustomerType;
import com.bancario.account.exception.BusinessException;
import com.bancario.account.mapper.AccountMapper;
import com.bancario.account.repository.AccountRepository;
import com.bancario.account.repository.entity.Account;
import com.bancario.account.service.AccountService;
import io.quarkus.mongodb.panache.common.reactive.ReactivePanacheUpdate;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.bson.types.ObjectId;

@Slf4j
@ApplicationScoped
public class AccountServiceImpl implements AccountService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    AccountMapper accountMapper;

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

    @Override
    public Uni<Void> incrementMonthlyTransactionCounter(String accountId) {
        // Llama al repositorio, que usa el comando atómico y devuelve Uni<Long> (el conteo).
        return accountRepository.incrementMonthlyTransactionCounter(accountId)
                // Usamos transformToUni para inspeccionar el resultado del conteo
                .onItem().transformToUni(updatedCount -> {
                    // Si el conteo es 0, significa que la cuenta no se encontró para actualizar.
                    if (updatedCount == 0) {
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
                    newAccount.setAccountNumber(UUID.randomUUID().toString());
                    newAccount.setOpeningDate(LocalDateTime.now());
                    newAccount.setStatus(AccountStatus.ACTIVE);
                    // 1. INICIALIZACIÓN DE COMISIÓN DE MANTENIMIENTO (DEFAULT)
                    newAccount.maintenanceFeeAmount = new BigDecimal("10.00");
                    newAccount.requiredDailyAverage = BigDecimal.ZERO;

                    // 2. INICIALIZACIÓN DE LÍMITES DE TRANSACCIÓN (DEFAULT: PERSONAL/EMPRESARIAL)
                    if (request.productType() == ProductType.PASSIVE) {
                        newAccount.freeTransactionLimit = 4; // 4 transacciones gratuitas por defecto
                        newAccount.transactionFeeAmount = new BigDecimal("0.50"); // Comisión de $0.50 por excedente
                        newAccount.currentMonthlyTransactions = 0; // Contador inicia en cero
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
                        newAccount.requiredDailyAverage = new BigDecimal("1000.00");
                        // Comisión Cero (sin comisión)
                        newAccount.maintenanceFeeAmount = BigDecimal.ZERO;
                        newAccount.freeTransactionLimit = 999; // Prácticamente ilimitado
                        newAccount.transactionFeeAmount = BigDecimal.ZERO; // Comisión de transacción Cero
                        log.info("Assigned VIP attributes: Avg. $1000.00, Fee $0.00, Txn Limit 999");
                    }
                    // LÓGICA DE ASIGNACIÓN PYME (Cuenta Corriente)
                    else if (customerResponse.type() == CustomerType.PYME &&
                            request.accountType() == AccountType.CURRENT_ACCOUNT) {
                        // Regla Especial PYME: Limite moderado con comisión baja
                        newAccount.freeTransactionLimit = 100; // Límite más alto que el estándar
                        newAccount.transactionFeeAmount = new BigDecimal("0.10"); // Comisión baja por excedente

                        log.info("Assigned PYME attributes: Fee $0.00, Txn Limit 100, Txn Fee $0.10");
                    }
                    return accountRepository.persist(newAccount)
                            .onItem().transform(accountMapper::toResponse);
                });
    }
}
package com.bancario.account.service.impl;

import com.bancario.account.client.CustomerServiceRestClient;
import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.enums.AccountStatus;
import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.ProductType;
import com.bancario.account.enums.CustomerType;
import com.bancario.account.mapper.AccountMapper;
import com.bancario.account.repository.AccountRepository;
import com.bancario.account.repository.entity.Account;
import com.bancario.account.service.AccountService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

        // 3. Encadena la creación de la cuenta solo si las validaciones asíncronas se completan.
        return validationUni.chain(() -> createAccount(request));
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

    // Método para la validación de cuentas activas (créditos)
    private Uni<Void> validateActiveAccountCreation(AccountRequest request) {
        return customerServiceRestClient.getCustomerById(request.customerId())
                .onItem().transformToUni(customerResponse -> {
                    if (customerResponse.type() == CustomerType.PERSONAL) {
                        return accountRepository.find("customerId = ?1 and productType = ?2", request.customerId(), ProductType.ACTIVE)
                                .count()
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
                    if (customerResponse.type() == CustomerType.PERSONAL) {
                        return accountRepository.find("customerId = ?1 and accountType = ?2", request.customerId(), request.accountType())
                                .count()
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

    // Método que contiene la lógica de persistencia y mapeo de la cuenta
    private Uni<AccountResponse> createAccount(AccountRequest request) {
        Account account = accountMapper.toEntity(request);
        account.setAccountNumber(UUID.randomUUID().toString());
        account.setOpeningDate(LocalDateTime.now());
        account.setStatus(AccountStatus.ACTIVE);
        return accountRepository.persist(account)
                .onItem().invoke(persistedAccount -> log.info("Account with ID {} created successfully", persistedAccount.id))
                .onItem().transform(persistedAccount -> accountMapper.toResponse(persistedAccount));
    }

    // Método para validaciones síncronas de la solicitud
    private void validateSynchronousAccountCreation(AccountRequest request) {
        if (request.balance() == null || request.balance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be null or negative.");
        }

        if (request.productType() == ProductType.PASSIVE) {
            if (request.accountType() == AccountType.CURRENT_ACCOUNT) {
                if (request.balance().compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("Current accounts require a non-zero initial balance.");
                }
            } else if (request.accountType() == AccountType.FIXED_TERM_DEPOSIT) {
                if (request.specificDepositDate() == null) {
                    throw new IllegalArgumentException("Fixed-term deposits require a specific deposit date.");
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
}
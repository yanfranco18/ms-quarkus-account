package com.bancario.account.repository.entity;

import com.bancario.account.enums.*;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@MongoEntity(collection = "accounts")
public class Account {
    public ObjectId id;
    public String customerId;
    public String accountNumber;
    public ProductType productType; // Nuevo: Para diferenciar Pasivo/Activo
    public AccountType accountType; // Opcional: Solo para productos pasivos
    public CreditType creditType;   // Opcional: Solo para productos activos
    public BigDecimal balance;
    public LocalDateTime openingDate;
    public Integer monthlyMovements; // Opcional: Para cuentas de ahorro
    public LocalDateTime specificDepositDate; // Opcional: Para plazo fijo
    public AccountStatus status;
    // Campos corregidos para titulares y firmantes
    private List<String> holders;
    private List<String> signatories;
}

package com.bancario.account.repository.entity;

import com.bancario.account.enums.AccountType;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidad de Persistencia que almacena la foto (snapshot) del saldo y uso de un producto financiero
 * al cierre del día (End-of-Day - EOD).
 * * Esta entidad es la fuente de datos para cálculos analíticos de promedios diarios.
 * * Se persiste en una colección separada ('balance_snapshots') para garantizar la escalabilidad
 * y separación del estado transaccional (Entidad Account).
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = "balance_snapshots")
public class BalanceSnapshot {
    public ObjectId id;
    public String customerId;
    public String productId;
    public AccountType accountType;
    public String productType;
    public LocalDate date;
    public BigDecimal balanceEOD;
    public BigDecimal amountUsedEOD;
}
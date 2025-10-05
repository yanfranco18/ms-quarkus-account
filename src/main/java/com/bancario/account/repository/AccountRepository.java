package com.bancario.account.repository;

import com.bancario.account.enums.AccountStatus;
import com.bancario.account.enums.AccountType;
import com.bancario.account.enums.CreditType;
import com.bancario.account.enums.ProductType;
import com.bancario.account.repository.entity.Account;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import io.quarkus.mongodb.panache.common.reactive.ReactivePanacheUpdate;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.ObjectId;

@ApplicationScoped
public class AccountRepository implements ReactivePanacheMongoRepository<Account> {
    /**
     * Verifica asíncronamente si el cliente tiene al menos una cuenta de crédito (tarjeta) activa.
     * Esta es una consulta personalizada que Panache ejecuta de forma reactiva.
     * * @param customerId El ID del cliente a verificar.
     * @return Uni<Boolean> que emitirá 'true' si encuentra una tarjeta activa, 'false' si no.
     */
    public Uni<Boolean> hasActiveCreditCard(String customerId) {

        // La consulta busca una cuenta que sea:
        // 1. Del cliente especificado (customerId)
        // 2. Un Producto ACTIVO (productType.ACTIVE)
        // 3. Específicamente una Tarjeta de Crédito (AccountType.CREDIT_CARD)
        return find("customerId = ?1 and productType = ?2 and creditType = ?3 and status = ?4",
                customerId,
                ProductType.ACTIVE,
                CreditType.CREDIT_CARD, // Asumiendo que existe CreditType.CREDIT_CARD
                AccountStatus.ACTIVE)    // <--- Es crucial que esté aquí
                .count()
                .onItem().transform(count -> count >= 1);
    }

    // --- Método 1: Contar Cuentas Pasivas por Tipo (Para clientes PERSONAL y VIP) ---
    public Uni<Long> countAccountsByType(String customerId, AccountType type) {
        return find("customerId = ?1 and accountType = ?2", customerId, type)
                .count();
    }

    // --- Método 2: Contar Productos Activos (Para la validación ACTIVE) ---
    public Uni<Long> countActiveProducts(String customerId) {
        return find("customerId = ?1 and productType = ?2", customerId, ProductType.ACTIVE)
                .count();
    }

    /**
     * Incrementa atómicamente el contador mensual de transacciones.
     * Utiliza el método 'update' de Panache con sintaxis String/JSON para la actualización.
     */
    public Uni<Long> incrementMonthlyTransactionCounter(String id) {

        ObjectId objectId = new ObjectId(id);

        // 1. Comando de actualización atómica ($inc) como String.
        String updateCommandString = "{$inc: {currentMonthlyTransactions: 1}}";

        // 2. Llamada a update() con el comando y el parámetro de filtro implícito (por ID).
        // 3. .all() finaliza la operación y la convierte a Uni<Long>.
        return update(
                updateCommandString,
                objectId
        ).all();
    }
}

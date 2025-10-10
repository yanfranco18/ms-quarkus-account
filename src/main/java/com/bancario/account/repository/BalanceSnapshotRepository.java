package com.bancario.account.repository;

import com.bancario.account.repository.entity.BalanceSnapshot;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio dedicado exclusivamente a la consulta y persistencia de las fotos del saldo
 * al final del día (BalanceSnapshot), que son la fuente de datos para los reportes analíticos.
 * Sigue el Principio de Responsabilidad Única (SRP) al manejar una colección separada.
 */
@ApplicationScoped
public class BalanceSnapshotRepository implements ReactivePanacheMongoRepository<BalanceSnapshot> {

    /**
     * Busca asíncronamente todos los snapshots de saldo (EOD) para los productos
     * de un cliente dentro de un rango de fechas específico.
     *
     * @param customerId El ID del cliente a consultar.
     * @param startDate La fecha de inicio del rango (inclusiva).
     * @param endDate La fecha de fin del rango (inclusiva).
     * @return Uni que emitirá una lista de la entidad BalanceSnapshot con los datos de historial.
     */
    public Uni<List<BalanceSnapshot>> findByCustomerAndDateRange(
            String customerId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // Consulta Panache usando la sintaxis de campo de Mongo.
        // Se ordena por fecha ascendente para facilitar el procesamiento posterior en el servicio.
        String query = "customerId = ?1 and date >= ?2 and date <= ?3 order by date asc";

        // El método list() es reactivo y devuelve el resultado envuelto en un Uni.
        return find(query, customerId, startDate, endDate).list();
    }
}
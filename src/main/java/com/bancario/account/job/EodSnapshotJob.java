package com.bancario.account.job;

import com.bancario.account.repository.AccountRepository;
import com.bancario.account.repository.BalanceSnapshotRepository;
import com.bancario.account.repository.entity.Account;
import com.bancario.account.repository.entity.BalanceSnapshot;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job programado que se ejecuta diariamente para tomar una 'foto' (snapshot) del saldo EOD
 * de todas las cuentas y productos activos.
 * * Este proceso garantiza que la colección balance_snapshots se llene con datos históricos
 * para el cálculo del Saldo Promedio Diario (SPD), cumpliendo con el Principio de Responsabilidad Única.
 */
@ApplicationScoped
public class EodSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(EodSnapshotJob.class);

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository snapshotRepository;

    @Inject
    public EodSnapshotJob(AccountRepository accountRepository, BalanceSnapshotRepository snapshotRepository) {
        this.accountRepository = accountRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Ejecuta el proceso de generación de snapshots diarios.
     * Programado para ejecutarse todos los días a las 22:00 PM (10:00 PM).
     */
    // CRON MODIFICADO: 0 0 22 * * ? (Segundos: 0, Minutos: 0, Hora: 22)
    @Scheduled(cron = "0 0 22 * * ?")
    public void runDailySnapshot() {
        LocalDate today = LocalDate.now();
        log.info("INICIO del Job EOD para la fecha: {}", today);

        // 1. Obtener todas las cuentas activas (o todos los productos).
        // NOTA: Asumimos un método findAllActiveProducts() en AccountRepository para obtener todas las cuentas.
        // Si no existe, usa accountRepository.findAll().list() y filtra si es necesario.
        Uni<Void> jobFlow = accountRepository.findAll().list() // Se asume que findAll().list() devuelve todas las cuentas.
                .onItem().transformToUni(accounts -> {
                    if (accounts.isEmpty()) {
                        log.warn("Job EOD: No se encontraron cuentas activas para procesar.");
                        return Uni.createFrom().voidItem(); // Flujo finalizado
                    }

                    // 2. Mapear las entidades Account a BalanceSnapshot
                    List<BalanceSnapshot> snapshots = accounts.stream()
                            .map(account -> mapAccountToSnapshot(account, today))
                            .toList();

                    // 3. Persistir la lista de snapshots de forma reactiva (batch insert)
                    log.info("Job EOD: Persistiendo {} snapshots para la fecha {}", snapshots.size(), today);

                    // El método persist() de Panache maneja la inserción batch.
                    return snapshotRepository.persist(snapshots)
                            .onItem().invoke(() -> log.info("Job EOD finalizado con éxito. Snapshots guardados: {}", snapshots.size()))
                            .onFailure().invoke(e -> log.error("Job EOD falló durante la persistencia de snapshots.", e));
                });

        // La suscripción es NECESARIA para que el flujo reactivo se ejecute.
        jobFlow.subscribe().with(
                success -> log.debug("Flujo reactivo del job completado para la fecha {}", today),
                failure -> log.error("Fallo general y no manejado en el flujo del Job EOD.", failure)
        );
    }

    // --- Lógica de Mapeo (Método de ayuda) ---

    /**
     * Mapea la entidad de estado actual (Account) a la entidad de historial (BalanceSnapshot).
     * Esta es la lógica de negocio del cierre diario.
     */
    private BalanceSnapshot mapAccountToSnapshot(Account account, LocalDate date) {
        BalanceSnapshot snapshot = new BalanceSnapshot();

        // Datos de identificación
        snapshot.customerId = account.customerId;
        snapshot.productId = account.id.toHexString();
        snapshot.productType = account.productType.toString();
        snapshot.accountType = account.accountType;
        snapshot.date = date;

        // Lógica clave para el saldo EOD:
        if (account.productType.toString().equals("PASSIVE")) { // Cuentas de depósito
            snapshot.balanceEOD = account.balance;
            snapshot.amountUsedEOD = BigDecimal.ZERO;
        } else { // ACTIVE (Créditos)
            snapshot.balanceEOD = account.balance; // Línea total de crédito (saldo)
            snapshot.amountUsedEOD = account.amountUsed; // Cantidad utilizada
        }

        return snapshot;
    }
}
package com.bancario.account.mapper;

import com.bancario.account.dto.DailyBalanceHistoryDto;
import com.bancario.account.repository.entity.BalanceSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface BalanceSnapshotMapper {

    /**
     * Mapea la Entidad de historial de saldo (BalanceSnapshot) al DTO de transferencia (DailyBalanceHistoryDto).
     * Nota: MapStruct se encarga automáticamente de convertir el Enum AccountType del source a String en el target.
     */
    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "accountType", source = "accountType") // Mapea Enum a String automáticamente
    @Mapping(target = "productType", source = "productType")
    @Mapping(target = "date", source = "date")
    @Mapping(target = "balanceEOD", source = "balanceEOD")
    @Mapping(target = "amountUsedEOD", source = "amountUsedEOD")
    DailyBalanceHistoryDto toDto(BalanceSnapshot snapshot);

    /**
     * Permite mapear listas completas de entidades a listas de DTOs.
     */
    List<DailyBalanceHistoryDto> toDtoList(List<BalanceSnapshot> snapshots);
}
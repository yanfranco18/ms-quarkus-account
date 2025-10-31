package com.bancario.account.mapper;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.repository.entity.Account;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "cdi")
public interface AccountMapper {

    // --- Mapeo de Solicitud (Request) a Entidad (Entity) ---
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "openingDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "monthlyMovements", ignore = true) // No viene en el Request

    // 🔑 CORRECCIÓN: Permitir el mapeo de amountUsed desde el Request
    @Mapping(target = "amountUsed", source = "amountUsed")

    @Mapping(target = "overdueAmount", ignore = true) // Inicialización en el servicio (siempre ZERO)
    @Mapping(target = "paymentDayOfMonth", source = "paymentDayOfMonth")
    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType")
    Account toEntity(AccountRequest request);

    // --- Mapeo de Entidad (Entity) a Respuesta (Response) ---
    @Mapping(target = "id", source = "id", qualifiedByName = "mapObjectIdToString")
    // Campos de riesgo mapeados desde la Entidad
    @Mapping(target = "paymentDayOfMonth", source = "paymentDayOfMonth")
    @Mapping(target = "overdueAmount", source = "overdueAmount")
    @Mapping(target = "amountUsed", source = "amountUsed")
    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType")
    @Mapping(target = "monthlyMovements", source = "monthlyMovements")
    AccountResponse toResponse(Account account);

    // Método corregido con la anotación @Named
    @Named("mapObjectIdToString")
    default String mapObjectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    default ObjectId mapStringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
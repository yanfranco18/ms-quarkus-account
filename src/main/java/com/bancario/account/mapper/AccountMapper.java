package com.bancario.account.mapper;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.repository.entity.Account;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named; // Asegúrate de importar esta anotación

@Mapper(componentModel = "cdi")
public interface AccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "openingDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType")
    @Mapping(target = "monthlyMovements", ignore = true)
    @Mapping(target = "amountUsed", ignore = true)
    Account toEntity(AccountRequest request);

    @Mapping(target = "id", source = "id", qualifiedByName = "mapObjectIdToString")
    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType")
    @Mapping(target = "monthlyMovements", ignore = true)
    @Mapping(target = "amountUsed", source = "amountUsed")
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
package com.bancario.account.mapper;

import com.bancario.account.dto.AccountRequest;
import com.bancario.account.dto.AccountResponse;
import com.bancario.account.repository.entity.Account;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface AccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "openingDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType") // <-- Mapeo añadido
    @Mapping(target = "monthlyMovements", ignore = true)
    Account toEntity(AccountRequest request);

    @Mapping(target = "holders", source = "holders")
    @Mapping(target = "signatories", source = "signatories")
    @Mapping(target = "creditType", source = "creditType") // <-- Mapeo añadido
    @Mapping(target = "monthlyMovements", ignore = true)
    AccountResponse toResponse(Account account);

    default String mapObjectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    default ObjectId mapStringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
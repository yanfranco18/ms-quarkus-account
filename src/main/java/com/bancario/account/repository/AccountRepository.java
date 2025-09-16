package com.bancario.account.repository;

import com.bancario.account.repository.entity.Account;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountRepository implements ReactivePanacheMongoRepository<Account> {
}

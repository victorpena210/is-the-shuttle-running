package com.victorpena.shuttleflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.UserAccount;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
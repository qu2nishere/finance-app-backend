package com.quan.userservice.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID>{
    Optional<Credential> findByUserId(UUID userId);

}
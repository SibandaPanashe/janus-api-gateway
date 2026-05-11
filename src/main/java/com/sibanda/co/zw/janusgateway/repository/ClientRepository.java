package com.sibanda.co.zw.janusgateway.repository;

import com.sibanda.co.zw.janusgateway.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, String> {
    Optional<ClientEntity> findByApiKeyHash(String apiKeyHash);
    Optional<ClientEntity> findByClientId(String clientId);
    boolean existsByApiKeyHash(String apiKeyHash);
}
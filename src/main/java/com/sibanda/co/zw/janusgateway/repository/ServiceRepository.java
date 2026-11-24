package com.sibanda.co.zw.janusgateway.repository;

import com.sibanda.co.zw.janusgateway.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    List<ServiceEntity> findByClientId(String clientId);
}

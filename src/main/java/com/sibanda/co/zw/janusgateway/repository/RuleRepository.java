package com.sibanda.co.zw.janusgateway.repository;

import com.sibanda.co.zw.janusgateway.entity.RuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, String> {
    List<RuleEntity> findByActiveTrueOrderByPriorityDesc();
    List<RuleEntity> findByActiveTrueOrderByPriorityAsc();
}
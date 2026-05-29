package com.hik.osp.repository;

import com.hik.osp.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {
}

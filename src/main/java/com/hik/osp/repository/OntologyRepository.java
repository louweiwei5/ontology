package com.hik.osp.repository;

import com.hik.osp.entity.OntologyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OntologyRepository extends JpaRepository<OntologyEntity, String> {
    Optional<OntologyEntity> findByName(String name);
    boolean existsByName(String name);
}

package com.hik.osp.repository;

import com.hik.osp.entity.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<PropertyEntity, String> {
    List<PropertyEntity> findByOntologyId(String ontologyId);
    long countByOntologyId(String ontologyId);
    Optional<PropertyEntity> findByOntologyIdAndName(String ontologyId, String name);
    Optional<PropertyEntity> findByOntologyIdAndNameAndDomainClassId(
            String ontologyId, String name, String domainClassId);

    @Modifying
    @Query("DELETE FROM PropertyEntity p WHERE p.ontologyId = :ontologyId")
    void deleteByOntologyId(@Param("ontologyId") String ontologyId);
}

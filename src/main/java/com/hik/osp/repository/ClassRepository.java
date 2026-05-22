package com.hik.osp.repository;

import com.hik.osp.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, String> {
    List<ClassEntity> findByOntologyId(String ontologyId);
    Optional<ClassEntity> findByOntologyIdAndName(String ontologyId, String name);
    long countByOntologyId(String ontologyId);

    @Modifying
    @Query("UPDATE ClassEntity c SET c.parentClassId = null WHERE c.parentClassId = :classId")
    void nullifyParentReferences(@Param("classId") String classId);

    @Modifying
    @Query("UPDATE PropertyEntity p SET p.domainClassId = null WHERE p.domainClassId = :classId")
    void nullifyDomainClassReferences(@Param("classId") String classId);
}

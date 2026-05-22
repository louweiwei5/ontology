package com.hik.osp.repository;

import com.hik.osp.entity.TableImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableImportRepository extends JpaRepository<TableImportEntity, String> {
    List<TableImportEntity> findByOntologyIdOrderByCreatedAtDesc(String ontologyId);
    List<TableImportEntity> findByOntologyIdAndStatus(String ontologyId, String status);
    List<TableImportEntity> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("DELETE FROM TableImportEntity t WHERE t.ontologyId = :ontologyId")
    void deleteByOntologyId(@Param("ontologyId") String ontologyId);

    @Modifying
    @Query("DELETE FROM TableImportEntity t WHERE t.dbConnectionId = :dbConnectionId")
    void deleteByDbConnectionId(@Param("dbConnectionId") String dbConnectionId);
}

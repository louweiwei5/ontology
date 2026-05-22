package com.hik.osp.repository;

import com.hik.osp.entity.DbConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnectionEntity, String> {
    List<DbConnectionEntity> findAllByOrderByCreatedAtDesc();
}

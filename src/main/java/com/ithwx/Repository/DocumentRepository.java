package com.ithwx.Repository;

import com.ithwx.Entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository
        extends JpaRepository<DocumentEntity, Long> {
}
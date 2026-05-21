package com.restaurant.pos.sequence.repository;

import com.restaurant.pos.sequence.domain.DocumentSequence;
import com.restaurant.pos.sequence.domain.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    List<DocumentSequence> findByClientId(UUID clientId);

    @Query("""
            SELECT ds FROM DocumentSequence ds
            WHERE ds.clientId = :clientId
              AND ((:orgId IS NULL AND ds.orgId IS NULL) OR ds.orgId = :orgId)
            """)
    List<DocumentSequence> findByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
    
    @Query("""
            SELECT ds FROM DocumentSequence ds
            WHERE ds.clientId = :clientId
              AND ((:orgId IS NULL AND ds.orgId IS NULL) OR ds.orgId = :orgId)
              AND ds.documentType = :documentType
            """)
    Optional<DocumentSequence> findByClientIdAndOrgIdAndDocumentType(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("documentType") DocumentType documentType);

    // CRITICAL for preventing duplicates: Pessimistic write lock blocks other threads until transaction completes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ds FROM DocumentSequence ds
            WHERE ds.clientId = :clientId
              AND ((:orgId IS NULL AND ds.orgId IS NULL) OR ds.orgId = :orgId)
              AND ds.documentType = :documentType
            """)
    Optional<DocumentSequence> findAndLockByDocumentType(
            @Param("clientId") UUID clientId, 
            @Param("orgId") UUID orgId, 
            @Param("documentType") DocumentType documentType
    );
}

package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {
    
    Page<MediaAsset> findByClientId(UUID clientId, Pageable pageable);
    
    @Query("SELECT m FROM MediaAsset m WHERE m.clientId = :clientId " +
           "AND (:assetType IS NULL OR m.assetType = :assetType) " +
           "AND (:uploadedBy IS NULL OR m.uploadedBy = :uploadedBy)")
    Page<MediaAsset> findByClientIdAndFilters(
        @Param("clientId") UUID clientId,
        @Param("assetType") MediaAsset.AssetType assetType,
        @Param("uploadedBy") String uploadedBy,
        Pageable pageable
    );
    
    Optional<MediaAsset> findByIdAndClientId(String id, UUID clientId);
    
    List<MediaAsset> findByClientIdAndAssetType(UUID clientId, MediaAsset.AssetType assetType);
}



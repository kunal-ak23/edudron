package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Institute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstituteRepository extends JpaRepository<Institute, String> {
    
    List<Institute> findByClientId(UUID clientId);
    
    List<Institute> findByClientIdAndIsActive(UUID clientId, Boolean isActive);
    
    Optional<Institute> findByIdAndClientId(String id, UUID clientId);
    
    Optional<Institute> findByClientIdAndCode(UUID clientId, String code);
    
    boolean existsByClientIdAndCode(UUID clientId, String code);
}


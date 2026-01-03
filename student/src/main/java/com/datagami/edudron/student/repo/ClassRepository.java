package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Class;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassRepository extends JpaRepository<Class, String> {
    
    List<Class> findByInstituteId(String instituteId);
    
    List<Class> findByInstituteIdAndIsActive(String instituteId, Boolean isActive);
    
    List<Class> findByClientId(UUID clientId);
    
    List<Class> findByClientIdAndIsActive(UUID clientId, Boolean isActive);
    
    Optional<Class> findByIdAndClientId(String id, UUID clientId);
    
    Optional<Class> findByInstituteIdAndCode(String instituteId, String code);
    
    boolean existsByInstituteIdAndCode(String instituteId, String code);
    
    @Query("SELECT COUNT(c) FROM Class c WHERE c.instituteId = :instituteId")
    long countByInstituteId(@Param("instituteId") String instituteId);
}


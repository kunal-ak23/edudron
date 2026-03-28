package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, String> {

    Page<Certificate> findByClientIdAndIsActiveTrue(UUID clientId, Pageable pageable);

    Page<Certificate> findByClientIdAndCourseIdAndIsActiveTrue(UUID clientId, String courseId, Pageable pageable);

    Page<Certificate> findByClientIdAndSectionIdAndIsActiveTrue(UUID clientId, String sectionId, Pageable pageable);

    Page<Certificate> findByClientIdAndSectionIdAndCourseIdAndIsActiveTrue(UUID clientId, String sectionId, String courseId, Pageable pageable);

    List<Certificate> findByClientIdAndStudentIdAndIsActiveTrue(UUID clientId, String studentId);

    Optional<Certificate> findByCredentialId(String credentialId);

    Optional<Certificate> findByClientIdAndStudentIdAndCourseIdAndIsActiveTrue(UUID clientId, String studentId, String courseId);

    List<Certificate> findByClientIdAndCourseIdAndSectionIdAndIsActiveTrue(UUID clientId, String courseId, String sectionId);
}

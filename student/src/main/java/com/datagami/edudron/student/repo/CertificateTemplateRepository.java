package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, String> {

    List<CertificateTemplate> findByClientIdAndIsActiveTrue(UUID clientId);

    List<CertificateTemplate> findByClientIdIsNullAndIsDefaultTrueAndIsActiveTrue();
}

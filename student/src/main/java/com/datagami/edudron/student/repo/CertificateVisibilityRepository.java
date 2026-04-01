package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CertificateVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateVisibilityRepository extends JpaRepository<CertificateVisibility, String> {

    Optional<CertificateVisibility> findByCertificateId(String certificateId);
}

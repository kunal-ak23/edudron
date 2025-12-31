package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByIsActiveTrueOrderByName();
    Optional<Client> findBySlug(String slug);
    List<Client> findAllByOrderByName();
}


package com.datagami.edudron.content.simulation.repo;

import com.datagami.edudron.content.simulation.domain.Simulation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SimulationRepository extends JpaRepository<Simulation, String>,
        JpaSpecificationExecutor<Simulation> {

    Page<Simulation> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    Page<Simulation> findByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId,
            Simulation.SimulationStatus status, Pageable pageable);

    List<Simulation> findByClientIdAndStatus(UUID clientId, Simulation.SimulationStatus status);

    Optional<Simulation> findByIdAndClientId(String id, UUID clientId);

    List<Simulation> findByCourseIdAndClientIdAndStatus(String courseId, UUID clientId,
            Simulation.SimulationStatus status);
}

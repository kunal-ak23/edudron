package com.datagami.edudron.content.simulation.repo;

import com.datagami.edudron.content.simulation.domain.SimulationPlay;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SimulationPlayRepository extends JpaRepository<SimulationPlay, String>,
        JpaSpecificationExecutor<SimulationPlay> {

    List<SimulationPlay> findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(
            String simulationId, String studentId);

    Optional<SimulationPlay> findByIdAndStudentId(String id, String studentId);

    int countBySimulationIdAndStudentId(String simulationId, String studentId);

    List<SimulationPlay> findByStudentIdAndClientIdOrderByStartedAtDesc(String studentId,
            UUID clientId);

    Optional<SimulationPlay> findTopBySimulationIdAndStudentIdOrderByFinalScoreDesc(
            String simulationId, String studentId);

    long countBySimulationId(String simulationId);
}

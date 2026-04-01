package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectEventGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEventGradeRepository extends JpaRepository<ProjectEventGrade, String> {

    List<ProjectEventGrade> findByEventIdAndClientId(String eventId, UUID clientId);

    List<ProjectEventGrade> findByStudentIdAndEventIdIn(String studentId, List<String> eventIds);

    List<ProjectEventGrade> findByClientIdAndEventIdIn(UUID clientId, List<String> eventIds);
}

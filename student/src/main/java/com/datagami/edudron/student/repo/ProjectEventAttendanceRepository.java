package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectEventAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEventAttendanceRepository extends JpaRepository<ProjectEventAttendance, String> {

    List<ProjectEventAttendance> findByEventIdAndClientId(String eventId, UUID clientId);

    List<ProjectEventAttendance> findByStudentIdAndEventIdIn(String studentId, List<String> eventIds);
}

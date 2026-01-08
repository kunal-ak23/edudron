package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.IssueReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueReportRepository extends JpaRepository<IssueReport, String> {
    List<IssueReport> findByClientIdAndStudentIdAndLectureId(UUID clientId, String studentId, String lectureId);
    List<IssueReport> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
    List<IssueReport> findByClientIdAndStatus(UUID clientId, IssueReport.IssueStatus status);
}


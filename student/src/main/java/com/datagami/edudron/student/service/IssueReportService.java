package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.IssueReport;
import com.datagami.edudron.student.dto.CreateIssueReportRequest;
import com.datagami.edudron.student.dto.IssueReportDTO;
import com.datagami.edudron.student.repo.IssueReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class IssueReportService {
    
    @Autowired
    private IssueReportRepository issueReportRepository;
    
    public IssueReportDTO createIssueReport(String studentId, CreateIssueReportRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        IssueReport issueReport = new IssueReport();
        issueReport.setId(UlidGenerator.nextUlid());
        issueReport.setClientId(clientId);
        issueReport.setStudentId(studentId);
        issueReport.setLectureId(request.getLectureId());
        issueReport.setCourseId(request.getCourseId());
        issueReport.setIssueType(request.getIssueType());
        issueReport.setDescription(request.getDescription());
        issueReport.setStatus(IssueReport.IssueStatus.OPEN);
        
        IssueReport saved = issueReportRepository.save(issueReport);
        return toDTO(saved);
    }
    
    public List<IssueReportDTO> getIssueReportsByLecture(String studentId, String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<IssueReport> reports = issueReportRepository.findByClientIdAndStudentIdAndLectureId(
            clientId, studentId, lectureId);
        return reports.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<IssueReportDTO> getIssueReportsByCourse(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<IssueReport> reports = issueReportRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        return reports.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    private IssueReportDTO toDTO(IssueReport issueReport) {
        IssueReportDTO dto = new IssueReportDTO();
        dto.setId(issueReport.getId());
        dto.setClientId(issueReport.getClientId());
        dto.setStudentId(issueReport.getStudentId());
        dto.setLectureId(issueReport.getLectureId());
        dto.setCourseId(issueReport.getCourseId());
        dto.setIssueType(issueReport.getIssueType());
        dto.setDescription(issueReport.getDescription());
        dto.setStatus(issueReport.getStatus());
        dto.setCreatedAt(issueReport.getCreatedAt());
        dto.setUpdatedAt(issueReport.getUpdatedAt());
        return dto;
    }
}


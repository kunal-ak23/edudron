package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectGroupMemberRepository extends JpaRepository<ProjectGroupMember, String> {

    List<ProjectGroupMember> findByGroupIdAndClientId(String groupId, UUID clientId);

    List<ProjectGroupMember> findByStudentIdAndClientId(String studentId, UUID clientId);

    void deleteByGroupId(String groupId);
}

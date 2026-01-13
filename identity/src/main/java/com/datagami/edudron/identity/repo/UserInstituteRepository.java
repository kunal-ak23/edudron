package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.UserInstitute;
import com.datagami.edudron.identity.domain.UserInstituteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserInstituteRepository extends JpaRepository<UserInstitute, UserInstituteId> {
    List<UserInstitute> findByUserId(String userId);
    List<UserInstitute> findByInstituteId(String instituteId);
    void deleteByUserId(String userId);
    boolean existsByUserIdAndInstituteId(String userId, String instituteId);
}

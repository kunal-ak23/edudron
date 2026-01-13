package com.datagami.edudron.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_institutes", schema = "idp")
@IdClass(UserInstituteId.class)
public class UserInstitute {
    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "institute_id", nullable = false)
    private String instituteId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UserInstitute() {
        this.createdAt = OffsetDateTime.now();
    }

    public UserInstitute(String userId, String instituteId) {
        this.userId = userId;
        this.instituteId = instituteId;
        this.createdAt = OffsetDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getInstituteId() {
        return instituteId;
    }

    public void setInstituteId(String instituteId) {
        this.instituteId = instituteId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.datagami.edudron.identity.domain;

import java.io.Serializable;
import java.util.Objects;

public class UserInstituteId implements Serializable {
    private String userId;
    private String instituteId;

    public UserInstituteId() {}

    public UserInstituteId(String userId, String instituteId) {
        this.userId = userId;
        this.instituteId = instituteId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserInstituteId that = (UserInstituteId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(instituteId, that.instituteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, instituteId);
    }
}

package com.datagami.edudron.student.dto;

public class CertificateVisibilityDTO {
    private boolean showScores;
    private boolean showProjectDetails;
    private boolean showOverallPercentage;
    private boolean showCourseName;

    public CertificateVisibilityDTO() {}

    // Getters and Setters
    public boolean isShowScores() { return showScores; }
    public void setShowScores(boolean showScores) { this.showScores = showScores; }

    public boolean isShowProjectDetails() { return showProjectDetails; }
    public void setShowProjectDetails(boolean showProjectDetails) { this.showProjectDetails = showProjectDetails; }

    public boolean isShowOverallPercentage() { return showOverallPercentage; }
    public void setShowOverallPercentage(boolean showOverallPercentage) { this.showOverallPercentage = showOverallPercentage; }

    public boolean isShowCourseName() { return showCourseName; }
    public void setShowCourseName(boolean showCourseName) { this.showCourseName = showCourseName; }
}

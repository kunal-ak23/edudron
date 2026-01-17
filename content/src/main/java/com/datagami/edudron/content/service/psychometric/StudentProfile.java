package com.datagami.edudron.content.service.psychometric;

import java.util.List;

/**
 * Student Profile - information from LMS
 */
public class StudentProfile {
    private String name;
    private Integer grade; // 8-12
    private String board;
    private String email;
    private List<String> courseHistory; // Course IDs or names
    
    public StudentProfile() {}
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    
    public String getBoard() { return board; }
    public void setBoard(String board) { this.board = board; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public List<String> getCourseHistory() { return courseHistory; }
    public void setCourseHistory(List<String> courseHistory) { this.courseHistory = courseHistory; }
}
